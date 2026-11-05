#!/usr/bin/env python3
"""Exercise Android in an isolated fleet guest; never a personal device."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import zipfile

from checks import CheckFailed, require

PACKAGE = 'com.ben.contactsonly'
FIXTURE = PACKAGE + '.test'
ORIGINAL_SHA256 = '6bd9fed2ffa9b5e89e29659726062f288fbbb0477b41365c32208d41b14e751e'
# Android 15 is the oldest supported version; see the README.
SUPPORTED_APIS = (35, 36)


def run(*args, timeout=60, check=True):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'{args!r}: {result.stdout[-2500:]} {result.stderr[-2500:]}')
    return result.stdout


def wait_for(probe, description, seconds=30):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        last = probe()
        if last:
            return last
        time.sleep(0.5)
    raise CheckFailed(f'{description}: deadline exceeded; last={last!r}')


def exercise(adb, output, record):
    apk = Path('app/build/outputs/apk/debug/app-debug.apk')
    tests = Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    original = Path('test-fixtures/contacts-only-1.0.apk')
    require(hashlib.sha256(original.read_bytes()).hexdigest() == ORIGINAL_SHA256,
            'Historical 1.0 fixture changed')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    key = Path('.ci-tooling/integration-signing/key.jks')
    key.parent.mkdir(parents=True, exist_ok=True)
    key.unlink(missing_ok=True)
    run('keytool', '-genkeypair', '-keystore', str(key), '-storepass', 'android',
        '-keypass', 'android', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '2', '-dname', 'CN=Disposable integration fixture')
    baseline = output / 'baseline-ephemeral-signed.apk'
    candidate = output / 'candidate-ephemeral-signed.apk'
    fixture_apk = output / 'fixture-ephemeral-signed.apk'
    for source, target in ((original, baseline), (apk, candidate), (tests, fixture_apk)):
        run(str(tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', 'fixture',
            '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', str(target), str(source))
        # Re-signing for disposable testing must preserve all executable/resource bytes.
        with zipfile.ZipFile(source) as old, zipfile.ZipFile(target) as resigned:
            for name in old.namelist():
                if not name.startswith('META-INF/'):
                    require(old.read(name) == resigned.read(name), 'Re-signing changed ' + name)
    record.update(app_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                  fixture_sha256=hashlib.sha256(tests.read_bytes()).hexdigest(),
                  phases=[], calls=[])
    def save():
        (output / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
    def role(name, package):
        adb('shell', 'cmd', 'role', 'add-role-holder', name, package)
        require(package in adb('shell', 'cmd', 'role', 'get-role-holders', name).splitlines(),
                f'{package} does not hold {name}')
    def phase(name):
        text = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'phase', name,
                   FIXTURE + '/com.ben.contactsonly.AndroidIntegration', timeout=90)
        (output / f'{len(record["phases"]):02}-{name}.txt').write_text(text)
        screenshot = subprocess.run(['adb', '-s', 'emulator-5554', 'exec-out', 'run-as',
            PACKAGE, 'cat', 'files/integration-screen.png'], capture_output=True, timeout=20)
        if screenshot.returncode == 0 and screenshot.stdout.startswith(b'\x89PNG'):
            (output / f'{len(record["phases"]):02}-{name}.png').write_bytes(screenshot.stdout)
        require('INSTRUMENTATION_RESULT: contacts_only=passed' in text, text)
        require('INSTRUMENTATION_CODE: -1' in text, text)
        record['phases'].append(name)
        save()
    def fixture(method, number, user=None):
        target = ['--user', user] if user is not None else []
        text = adb('shell', 'content', 'call', *target, '--uri', 'content://' + FIXTURE + '.fixture',
                   '--method', method, '--arg', number)
        require('json=' in text, text)
        return json.JSONDecoder().raw_decode(text.split('json=', 1)[1])[0]
    def observe(number):
        """Place a synthetic call and report what Telecom and call history show."""
        started = time.monotonic()
        adb('emu', 'gsm', 'call', number)
        try:
            def settled():
                value = fixture('call-state', number)
                return value if value.get('rang') or value.get('log_type') == 6 else None
            try:
                wait_for(settled, 'Call outcome for ' + number, 25)
            except CheckFailed:
                pass  # The caller decides whether an unsettled call is a failure.
            # Hold past Android's screening deadline; a transient event is insufficient.
            time.sleep(max(0, 6 - (time.monotonic() - started)))
            value = fixture('call-state', number)
            value['observation_seconds'] = time.monotonic() - started
            return value
        finally:
            adb('emu', 'gsm', 'cancel', number, check=False)
            time.sleep(1)
    def call(number, blocked, note):
        value = observe(number)
        if blocked:
            # Rejected: blocked history from the screening service, and the dialer never rang.
            require(value.get('log_type') == 6 and value.get('block_reason') == 1
                    and not value.get('rang'), f'{note}: expected rejection; observed {value}')
        else:
            require(value.get('state') == 2 and value.get('rang') and value.get('log_type') != 6,
                    f'{note}: expected ringing; observed {value}')
        record['calls'].append({'number': number, 'case': note, 'expected_blocked': blocked,
                                'observed': value})
        save()
    def work_profile_contact(number):
        """Characterise, without gating, a caller saved only in a managed work profile.

        Whether Android treats work contacts as contacts for screening is platform
        behaviour this app cannot change without READ_CONTACTS. Record the outcome.
        """
        observation = {'number': number}
        record['work_profile_contact'] = observation
        try:
            if 'feature:android.software.managed_users' not in adb('shell', 'pm', 'list', 'features'):
                observation.update(state='unverified', reason='managed_users feature absent')
                return
            created = adb('shell', 'pm', 'create-user', '--profileOf', '0', '--managed', 'ContactsOnlyWork')
            user = re.search(r'created user id (\d+)', created, re.I).group(1)
            adb('shell', 'pm', 'install-existing', '--user', user, FIXTURE)
            adb('shell', 'dpm', 'set-profile-owner', '--user', user,
                FIXTURE + '/com.ben.contactsonly.IntegrationAdminReceiver')
            adb('shell', 'am', 'start-user', '-w', user, timeout=120)
            for permission in ('READ_CONTACTS', 'WRITE_CONTACTS'):
                adb('shell', 'pm', 'grant', '--user', user, FIXTURE, 'android.permission.' + permission)
            require(fixture('seed-contact', number, user)['seeded'] == number, 'Work contact not seeded')
            value = observe(number)
            observation.update(state='observed', user=user, rang=bool(value.get('rang')),
                               rejected=value.get('log_type') == 6, observed=value)
        except Exception as error:  # An observation, not a gate: keep the reason.
            observation.update(state='unverified', error=repr(error))
        finally:
            save()

    adb('shell', 'input', 'keyevent', '82')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('install', '-r', str(baseline))
    role('android.app.role.CALL_SCREENING', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity')
    before = adb('shell', 'dumpsys', 'package', PACKAGE)
    first_install = re.search(r'firstInstallTime=(.+)', before).group(1)
    require('versionCode=1 ' in before, 'Baseline is not version 1')
    adb('install', '-r', str(candidate))
    after = adb('shell', 'dumpsys', 'package', PACKAGE)
    require('versionCode=2 ' in after and ('firstInstallTime=' + first_install) in after,
            'Candidate did not update the baseline in place')
    record['upgrade'] = {'from_version': 1, 'to_version': 2, 'first_install_preserved': True}
    adb('install', '-t', '-r', '-g', str(fixture_apk))
    for permission in ('READ_CONTACTS', 'WRITE_CONTACTS', 'READ_CALL_LOG'):
        adb('shell', 'pm', 'grant', FIXTURE, 'android.permission.' + permission)
    role('android.app.role.DIALER', FIXTURE)
    # Start provider before any instrumentation: exercise its real independent process/classloader.
    require(fixture('seed-contact', '+12025550101')['seeded'] == '+12025550101', 'Contact not seeded')
    record['fixture_cold_start'] = True
    # Contacts saved in a different format from the number the network presents.
    for saved in ('202-555-0106', '+12025550107'):
        require(fixture('seed-contact', saved)['seeded'] == saved, 'Contact not seeded: ' + saved)
    phase('upgrade-on')
    call('+12025550101', False, 'saved contact rings')  # Android bypasses screening for contacts.
    call('+12025550106', False, 'contact saved as national number, presented international')
    call('2025550107', False, 'contact saved as international number, presented national')
    call('+12025550102', True, 'unsaved caller rejected')
    phase('set-off')
    call('+12025550103', False, 'unsaved caller rings while paused')
    adb('shell', 'am', 'force-stop', PACKAGE)
    phase('verify-off')
    adb('install', '-r', str(candidate))
    phase('verify-off')
    adb('reboot')
    wait_for(lambda: adb('shell', 'getprop', 'sys.boot_completed', check=False).strip() == '1',
             'Reboot complete', 180)
    adb('shell', 'input', 'keyevent', '82')
    adb('shell', 'wm', 'dismiss-keyguard')
    phase('verify-off')
    phase('set-on')
    call('+12025550104', True, 'unsaved caller rejected after resuming')
    adb('shell', 'cmd', 'role', 'remove-role-holder', 'android.app.role.CALL_SCREENING', PACKAGE)
    phase('role-revoked')
    # The saved choice is still on, but without the role Android must not consult the app.
    call('+12025550105', False, 'unsaved caller rings after role revocation')
    role('android.app.role.CALL_SCREENING', PACKAGE)
    phase('verify-on')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
    adb('shell', 'wm', 'size', '720x1280')
    adb('shell', 'am', 'force-stop', PACKAGE)
    phase('large-text-on')
    record['large_text'] = {'font_scale': 2.0, 'display_pixels': '720x1280',
                            'switch_semantics_and_label': 'passed'}
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', 'reset')
    work_profile_contact('+12025550108')
    record['state'] = 'passed'
    save()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--api', type=int, choices=SUPPORTED_APIS, default=36)
    parser.add_argument('--probe-only', action='store_true')
    parser.add_argument('--if-installed', action='store_true',
                        help='record a pending result, instead of failing, when the image is absent')
    args = parser.parse_args()
    output = Path(os.environ['CI_ARTIFACTS_DIR']) / f'android-integration-{args.api}'
    output.mkdir(parents=True, exist_ok=True)
    image = f'system-images;android-{args.api};aosp_atd;x86_64'
    avd = f'contacts-only-{args.api}'
    if not (Path(os.environ['ANDROID_HOME']) / image.replace(';', '/')).is_dir():
        if not args.if_installed:
            raise CheckFailed('System image not installed: ' + image)
        pending = {'api': args.api, 'image': image, 'state': 'pending',
                   'reason': 'system image is not installed on this fleet guest; nothing was run'}
        (output / 'result.json').write_text(json.dumps(pending, indent=2) + '\n')
        print(json.dumps(pending), flush=True)
        return
    run('emulator', '-accel-check')
    subprocess.run(['avdmanager', 'create', 'avd', '--force', '--name', avd,
                    '--package', image, '--device', 'pixel_6'], input='no\n',
                   text=True, capture_output=True, check=True, timeout=60)
    with (output / 'emulator.log').open('w') as log:
        emulator = subprocess.Popen(['emulator', '-avd', avd, '-port', '5554',
                                     '-no-window', '-no-audio', '-no-boot-anim',
                                     '-no-snapshot', '-wipe-data', '-accel', 'on',
                                     '-gpu', 'swiftshader_indirect', '-cores', '2',
                                     '-memory', '2048'], stdout=log, stderr=subprocess.STDOUT)
        adb = lambda *cmd, **kw: run('adb', '-s', 'emulator-5554', *cmd, **kw)
        try:
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                if emulator.poll() is not None:
                    raise RuntimeError('Emulator exited before boot; inspect emulator.log')
                if adb('shell', 'getprop', 'sys.boot_completed', check=False).strip() == '1':
                    break
                time.sleep(2)
            else:
                raise TimeoutError('Android boot deadline exceeded')
            record = {'api': args.api, 'image': image,
                      'sdk': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
                      'features': adb('shell', 'pm', 'list', 'features'),
                      'packages': adb('shell', 'pm', 'list', 'packages', '-s')}
            require(record['sdk'] == str(args.api), record)
            record['fingerprint'] = adb('shell', 'getprop', 'ro.build.fingerprint').strip()
            if args.probe_only:
                record['incoming_console'] = adb('emu', 'gsm', 'call', '+12025550103')
                time.sleep(7)
                record['telecom'] = adb('shell', 'dumpsys', 'telecom')
                record['modem'] = adb('emu', 'gsm', 'list')
                adb('emu', 'gsm', 'cancel', '+12025550103', check=False)
                (output / 'probe.json').write_text(json.dumps(record, indent=2) + '\n')
            else:
                try:
                    exercise(adb, output, record)
                except Exception as error:
                    record.update(state='failed', error=repr(error))
                    (output / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
                    raise
            print(json.dumps({'api': args.api, 'sdk': record['sdk'],
                              'state': record.get('state', 'probe-retained'),
                              'work_profile_contact': record.get('work_profile_contact')}), flush=True)
        finally:
            diagnostics = {
                'telecom.txt': ('shell', 'dumpsys', 'telecom'),
                'app-logcat.txt': ('shell', 'logcat', '-d', '-t', '2000', '-s',
                                   'ContactsOnly:I', 'ContactsOnlyIntegration:E', 'AndroidRuntime:E')}
            for name, command in diagnostics.items():
                try:
                    (output / name).write_text(adb(*command, check=False, timeout=10))
                except Exception as error:
                    (output / name).write_text('Diagnostic unavailable: ' + repr(error))
            try:
                adb('emu', 'kill', check=False, timeout=10)
            except Exception:
                pass
            try:
                emulator.wait(timeout=20)
            except subprocess.TimeoutExpired:
                emulator.terminate()
                emulator.wait(timeout=20)


if __name__ == '__main__':
    main()

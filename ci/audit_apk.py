#!/usr/bin/env python3
"""Inspect the shipped artifacts rather than assuming source manifests are their contents.

Two artifacts ship: the unsigned release APK (GitHub) and the unsigned app
bundle (Google Play). Play serves APKs generated from the bundle, so the bundle
is audited through the universal APK that AGP generates from it, with the same
checks as the release APK, and both must carry the same version.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

from checks import require

RELEASE_APK = Path('app/build/outputs/apk/release/app-release-unsigned.apk')
BUNDLE = Path('app/build/outputs/bundle/release/app-release.aab')
BUNDLE_APK = Path('app/build/outputs/apk_from_bundle/release/app-release-universal-unsigned.apk')


def audit(aapt, apk):
    def dump(*args):
        return subprocess.check_output([str(aapt), 'dump', *args, str(apk)], text=True)
    permissions = dump('permissions')
    badging = dump('badging')
    manifest = dump('xmltree', '--file', 'AndroidManifest.xml')
    require('uses-permission:' not in permissions, permissions)
    require('application-debuggable' not in badging, badging)
    require("package: name='com.contactsonly.app'" in badging, badging)
    require("minSdkVersion:'29'" in badging, badging)
    require("targetSdkVersion:'36'" in badging, badging)
    require('android.permission.BIND_SCREENING_SERVICE' in manifest, manifest)
    require(manifest.count('E: activity ') == 1, manifest)
    require(manifest.count('E: service ') == 1, manifest)
    require('E: provider ' not in manifest and 'E: receiver ' not in manifest, manifest)
    require(re.search(r'android:allowBackup[^\n]*=(?:false|(?:\(type 0x12\))?0x0)(?:\s|$)', manifest),
            manifest)
    require('Integration' not in manifest and 'test.fixture' not in manifest, manifest)
    with zipfile.ZipFile(apk) as package:
        dex = [name for name in package.namelist() if name.endswith('.dex')]
        require(dex, 'No DEX in ' + str(apk))
        for name in dex:
            content = package.read(name)
            for marker in (b'IntegrationFixtureProvider', b'IntegrationAdminReceiver',
                           b'robolectric', b'mockito'):
                require(marker not in content, (str(apk), name, marker))
    version = re.search(r"versionCode='(\d+)' versionName='([^']*)'", badging)
    require(version, badging)
    return {'manifest': manifest, 'permissions': permissions,
            'version_code': int(version.group(1)), 'version_name': version.group(2)}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    aapt = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0/aapt2'
    release = audit(aapt, RELEASE_APK)
    require(BUNDLE.is_file(), 'Missing app bundle: ' + str(BUNDLE))
    with zipfile.ZipFile(BUNDLE) as bundle:
        require('base/manifest/AndroidManifest.xml' in bundle.namelist(), 'Bundle has no base module')
    from_bundle = audit(aapt, BUNDLE_APK)
    for key in ('version_code', 'version_name'):
        require(release[key] == from_bundle[key], (key, release[key], from_bundle[key]))
    out = Path(os.environ['CI_ARTIFACTS_DIR']) / 'apk-audit'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'manifest.txt').write_text(release['manifest'])
    (out / 'permissions.txt').write_text(release['permissions'])
    (out / 'bundle-manifest.txt').write_text(from_bundle['manifest'])
    record = {'state': 'passed', 'sha256': sha256(RELEASE_APK),
              'bundle_sha256': sha256(BUNDLE), 'bundle_universal_apk_sha256': sha256(BUNDLE_APK),
              'version_code': release['version_code'], 'version_name': release['version_name'],
              'permissions': [], 'debuggable': False, 'activities': 1, 'services': 1,
              'test_fixture_absent': True, 'min_sdk': 29, 'target_sdk': 36}
    (out / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record), flush=True)


if __name__ == '__main__':
    main()

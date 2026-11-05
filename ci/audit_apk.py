#!/usr/bin/env python3
"""Inspect the shipped artifact rather than assuming source manifests are its contents."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

from checks import require


def main():
    sdk = Path(os.environ['ANDROID_HOME'])
    aapt = sdk / 'build-tools/36.0.0/aapt2'
    apk = Path('app/build/outputs/apk/release/app-release-unsigned.apk')
    def dump(*args):
        return subprocess.check_output([str(aapt), 'dump', *args, str(apk)], text=True)
    permissions = dump('permissions')
    badging = dump('badging')
    manifest = dump('xmltree', '--file', 'AndroidManifest.xml')
    require('uses-permission:' not in permissions, permissions)
    require('application-debuggable' not in badging, badging)
    require("package: name='com.ben.contactsonly'" in badging, badging)
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
        for name in package.namelist():
            if name.endswith('.dex'):
                content = package.read(name)
                for marker in (b'IntegrationFixtureProvider', b'IntegrationAdminReceiver',
                               b'robolectric', b'mockito'):
                    require(marker not in content, (name, marker))
    out = Path(os.environ['CI_ARTIFACTS_DIR']) / 'apk-audit'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'manifest.txt').write_text(manifest)
    (out / 'permissions.txt').write_text(permissions)
    record = {'state': 'passed', 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'permissions': [], 'debuggable': False, 'activities': 1, 'services': 1,
              'test_fixture_absent': True, 'min_sdk': 29, 'target_sdk': 36}
    (out / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record), flush=True)


if __name__ == '__main__':
    main()

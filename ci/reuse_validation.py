#!/usr/bin/env python3
"""Continue native checks only when retained binaries match unchanged compiled inputs.

The passing build job writes ``reuse/binding.json`` itself (``--write-binding``),
so a binding is never assembled by hand. To reuse that build, stage the job's
complete fleet download (``ci-fleet.py --download-artifacts``) unchanged as
``.validation-inputs/`` and run this script without arguments. It checks:

* every downloaded file against the fleet's readback manifest, with no extras;
* that the binding names exactly the three APKs and the build's JUnit reports;
* every bound artifact's SHA-256, and passing JUnit totals equal to the binding's;
* the complete compiled-input inventory and every input's SHA-256.

Only then are the APKs restored and the orchestration tests and native checks run.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import xml.etree.ElementTree as ET

from checks import require

INPUTS = ['app/src', 'app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
          'gradle', 'gradlew', 'gradlew.bat', 'test-fixtures']
APKS = {
    'contacts-only/apk/debug/app-debug.apk': 'app/build/outputs/apk/debug/app-debug.apk',
    'contacts-only/apk/release/app-release-unsigned.apk':
        'app/build/outputs/apk/release/app-release-unsigned.apk',
    'contacts-only/apk/androidTest/debug/app-debug-androidTest.apk':
        'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
}
JUNIT_SOURCE = Path('app/build/test-results/testDebugUnitTest')
JUNIT_PREFIX = 'contacts-only/test-results/testDebugUnitTest/'
BINDING = 'artifacts/reuse/binding.json'
FORMAT = 2


def source_files():
    files = []
    for name in INPUTS:
        path = Path(name)
        require(path.exists(), 'Missing compiled input root: ' + name)
        values = path.rglob('*') if path.is_dir() else [path]
        for value in values:
            require(not value.is_symlink(), 'Symlink in compiled inputs: ' + str(value))
            if value.is_file():
                files.append(value.as_posix())
    return sorted(files)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def junit_totals(paths):
    tests = 0
    for path in paths:
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == 'testsuite' else list(root.iter('testsuite'))
        require(suites, 'No test suite in ' + str(path))
        for suite in suites:
            for key in ('failures', 'errors', 'skipped'):
                require(int(suite.get(key, '0')) == 0, f'JUnit {key} in {path}')
            tests += int(suite.get('tests', '0'))
    return tests


def safe_relative(value):
    path = PurePosixPath(value)
    require(value and not path.is_absolute() and '..' not in path.parts, 'Unsafe path: ' + repr(value))
    return path


def build_head():
    try:
        return subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True,
                                       stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def write_binding(target):
    """Run by the passing build job, before anything else touches its outputs."""
    reports = sorted(JUNIT_SOURCE.glob('TEST-*.xml'))
    require(reports, 'No JUnit reports to bind')
    artifacts = {key: digest(source) for key, source in APKS.items()}
    artifacts.update({JUNIT_PREFIX + p.name: digest(p) for p in reports})
    binding = {'format': FORMAT, 'produced_by': 'ci/validate.sh', 'build_head': build_head(),
               'compiled_inputs': {p: digest(p) for p in source_files()},
               'artifacts': artifacts,
               'junit': {'files': [JUNIT_PREFIX + p.name for p in reports],
                         'tests': junit_totals(reports)}}
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(binding, indent=2, sort_keys=True) + '\n')
    print(json.dumps({'state': 'binding-written', 'compiled_files': len(binding['compiled_inputs']),
                      'jvm_tests': binding['junit']['tests']}), flush=True)
    return binding


def verify_download(root):
    manifest = json.loads((root / 'readback-manifest.json').read_text())
    listed = {}
    for entry in manifest['files']:
        listed[safe_relative(entry['path']).as_posix()] = entry['sha256']
    present = {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() or p.is_symlink()}
    require(not any((root / p).is_symlink() for p in present), 'Symlink in staged download')
    present.discard('readback-manifest.json')
    require(present == set(listed), 'Staged download differs from its readback manifest: '
            + repr(sorted(present ^ set(listed))[:10]))
    for path, wanted in listed.items():
        require(digest(root / path) == wanted, 'Downloaded file changed: ' + path)
    return manifest


def verify(root):
    root = Path(root)
    manifest = verify_download(root)
    require(BINDING in {e['path'] for e in manifest['files']}, 'Build job retained no reuse binding')
    binding = json.loads((root / BINDING).read_text())
    require(binding.get('format') == FORMAT, 'Unsupported binding format')
    artifacts = binding['artifacts']
    junit = binding['junit']['files']
    require(junit and all(name.startswith(JUNIT_PREFIX) and name.endswith('.xml') for name in junit),
            'Binding names no valid JUnit reports')
    require(set(artifacts) == set(APKS) | set(junit),
            'Binding must name exactly the three APKs and the JUnit reports')
    for path, wanted in artifacts.items():
        require(digest(root / 'artifacts' / safe_relative(path)) == wanted, 'Artifact changed: ' + path)
    tests = junit_totals([root / 'artifacts' / name for name in junit])
    require(tests > 0 and tests == binding['junit']['tests'], 'JUnit totals differ from the binding')
    require(source_files() == sorted(binding['compiled_inputs']), 'Compiled input inventory changed')
    for path, wanted in binding['compiled_inputs'].items():
        require(digest(path) == wanted, 'Compiled input changed: ' + path)
    return manifest, binding


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write-binding', metavar='PATH')
    args = parser.parse_args()
    if args.write_binding:
        write_binding(args.write_binding)
        return
    root = Path('.validation-inputs')
    manifest, binding = verify(root)
    for key, target in APKS.items():
        destination = Path(target)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / 'artifacts' / key, destination)
    output = Path(os.environ['CI_ARTIFACTS_DIR']) / 'reuse'
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(root / BINDING, output / 'binding.json')
    shutil.copy2(root / 'readback-manifest.json', output / 'build-readback-manifest.json')
    print(json.dumps({'state': 'verified-reuse', 'build_job': manifest.get('job'),
                      'build_head': binding.get('build_head'),
                      'compiled_files': len(binding['compiled_inputs']),
                      'jvm_tests': binding['junit']['tests']}), flush=True)
    subprocess.run(['sh', 'ci/orchestration_tests.sh'], check=True)
    subprocess.run(['sh', 'ci/native_checks.sh'], check=True)


if __name__ == '__main__':
    main()

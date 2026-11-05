"""Exercise input/artifact boundary failures; no Android or build required."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from checks import CheckFailed
from reuse_validation import APKS, BINDING, INPUTS, JUNIT_PREFIX, JUNIT_SOURCE, digest, verify, write_binding

CI = Path(__file__).resolve().parent
PASSING = '<testsuite name="CallBlockingTest" tests="24" failures="0" errors="0" skipped="0"/>'


class ReuseBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.previous = Path.cwd()
        self.folder = tempfile.TemporaryDirectory()
        os.chdir(self.folder.name)
        for name in INPUTS:
            path = Path(name)
            if name in ('app/src', 'gradle', 'test-fixtures'):
                path.mkdir(parents=True, exist_ok=True)
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('fixture input\n')
        self.source = Path('app/src/main/java/Example.java')
        self.source.parent.mkdir(parents=True)
        self.source.write_text('class Example {}\n')
        for target in APKS.values():
            Path(target).parent.mkdir(parents=True, exist_ok=True)
            Path(target).write_bytes(b'identity-check fixture, not an executable APK: ' + target.encode())
        JUNIT_SOURCE.mkdir(parents=True)
        (JUNIT_SOURCE / 'TEST-CallBlockingTest.xml').write_text(PASSING)
        # Lay the build out as the fleet download does, then seal it.
        self.root = Path('.validation-inputs')
        self.binding_path = self.root / BINDING
        write_binding(self.binding_path)
        for key, source in APKS.items():
            self.copy(source, key)
        self.copy(JUNIT_SOURCE / 'TEST-CallBlockingTest.xml', JUNIT_PREFIX + 'TEST-CallBlockingTest.xml')
        (self.root / 'receipt.json').write_text('{"exit": 0}\n')
        self.seal()

    def tearDown(self):
        os.chdir(self.previous)
        self.folder.cleanup()

    def copy(self, source, key):
        target = self.root / 'artifacts' / key
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)

    def seal(self, extra=()):
        files = [{'path': p.relative_to(self.root).as_posix(), 'sha256': digest(p)}
                 for p in sorted(self.root.rglob('*'))
                 if p.is_file() and p.name != 'readback-manifest.json']
        (self.root / 'readback-manifest.json').write_text(
            json.dumps({'job': 'fixture-job', 'files': files + list(extra)}))

    def rebind(self, change):
        binding = json.loads(self.binding_path.read_text())
        change(binding)
        self.binding_path.write_text(json.dumps(binding))
        self.seal()

    def rejects(self, pattern):
        with self.assertRaisesRegex(CheckFailed, pattern):
            verify(self.root)

    def test_unchanged_inputs_are_accepted(self):
        manifest, binding = verify(self.root)
        self.assertEqual('fixture-job', manifest['job'])
        self.assertEqual(24, binding['junit']['tests'])

    def test_untracked_source_addition_is_rejected(self):
        # There is deliberately no Git index in this fixture.
        Path('app/src/main/java/New.java').write_text('class New {}')
        self.rejects('inventory changed')

    def test_deleted_source_is_rejected(self):
        self.source.unlink()
        self.rejects('inventory changed')

    def test_modified_source_is_rejected(self):
        self.source.write_text('class Example { int changed; }')
        self.rejects('Compiled input changed')

    def test_artifact_changed_after_download_is_rejected(self):
        (self.root / 'artifacts' / 'contacts-only/apk/debug/app-debug.apk').write_bytes(b'changed')
        self.rejects('Downloaded file changed')

    def test_artifact_differing_from_binding_is_rejected_even_when_resealed(self):
        (self.root / 'artifacts' / 'contacts-only/apk/debug/app-debug.apk').write_bytes(b'changed')
        self.seal()
        self.rejects('Artifact changed')

    def test_file_added_to_download_is_rejected(self):
        (self.root / 'artifacts' / 'extra.apk').write_bytes(b'unlisted')
        self.rejects('differs from its readback manifest')

    def test_binding_that_omits_an_apk_is_rejected(self):
        key = 'contacts-only/apk/androidTest/debug/app-debug-androidTest.apk'
        self.rebind(lambda binding: binding['artifacts'].pop(key))
        self.rejects('exactly the three APKs')

    def test_binding_that_omits_junit_is_rejected(self):
        def drop(binding):
            for name in binding['junit']['files']:
                binding['artifacts'].pop(name)
            binding['junit']['files'] = []
        self.rebind(drop)
        self.rejects('no valid JUnit')

    def test_failed_junit_is_rejected_even_with_matching_hashes(self):
        report = self.root / 'artifacts' / JUNIT_PREFIX / 'TEST-CallBlockingTest.xml'
        report.write_text(PASSING.replace('failures="0"', 'failures="1"'))
        self.rebind(lambda binding: binding['artifacts'].update(
            {JUNIT_PREFIX + report.name: digest(report)}))
        self.rejects('JUnit failures')

    def test_junit_count_differing_from_binding_is_rejected(self):
        self.rebind(lambda binding: binding['junit'].update(tests=36))
        self.rejects('JUnit totals differ')

    def test_unsafe_manifest_path_is_rejected(self):
        self.seal(extra=[{'path': '../outside', 'sha256': '0' * 64}])
        self.rejects('Unsafe path')

    def test_missing_binding_is_rejected(self):
        self.binding_path.unlink()
        self.seal()
        self.rejects('retained no reuse binding')

    def test_write_binding_requires_junit_reports(self):
        shutil.rmtree(JUNIT_SOURCE)
        with self.assertRaisesRegex(CheckFailed, 'No JUnit reports'):
            write_binding('unused.json')

    def test_gate_still_rejects_under_python_optimisation(self):
        self.source.write_text('class Example { int changed; }')
        code = (f'import sys; sys.path.insert(0, {str(CI)!r}); '
                'from reuse_validation import verify; verify(".validation-inputs")')
        result = subprocess.run([sys.executable, '-O', '-c', code], capture_output=True, text=True,
                                env={**os.environ, 'PYTHONOPTIMIZE': '2'})
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn('Compiled input changed', result.stderr)


if __name__ == '__main__':
    unittest.main()

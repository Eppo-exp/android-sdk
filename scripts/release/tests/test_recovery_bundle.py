import hashlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('release_bundle_subject', Path(__file__).parents[1] / 'release.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


class RecoveryBundleTest(unittest.TestCase):
    def fixture(self, root, artifact=r.FRAMEWORK):
        version = '0.1.0'
        folder = root / 'cloud/eppo' / artifact / version
        folder.mkdir(parents=True)
        files = {}
        for name in r.required_files(artifact, version):
            data = ('original bytes ' + name).encode()
            (folder / name).write_bytes(data)
            files[name] = hashlib.sha256(data).hexdigest()
        return {'schema': 1, 'artifact': artifact, 'version': version, 'state': 'attempting',
                'inputs': 'inputs', 'commit': 'commit', 'files': files}

    def test_original_bytes_survive_runner_loss_before_deployment(self):
        self.assert_preserved(r.FRAMEWORK)

    def test_sdk_bytes_survive_runner_loss_before_deployment(self):
        self.assert_preserved(r.COMMON)

    def assert_preserved(self, artifact):
        assets = {}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = self.fixture(root, artifact)
            def upload(command, **kwargs):
                path = Path(command[4])
                assets[path.name] = path.read_bytes()
                if path.suffix == '.json':
                    self.assertTrue(any(name.endswith('.zip') for name in assets),
                                    'Recovery bytes must be durable before intent')
                return subprocess.CompletedProcess(command, 0)
            with patch.object(r, 'STAGING', root), patch.object(r.subprocess, 'run', side_effect=upload):
                r.upload_record('v5.0.0', record)
        bundle = next(value for name, value in assets.items() if name.endswith('.zip'))
        with zipfile.ZipFile(io.BytesIO(bundle)) as archive:
            for name, checksum in record['files'].items():
                path = 'cloud/eppo/' + record['artifact'] + '/' + record['version'] + '/' + name
                self.assertEqual(hashlib.sha256(archive.read(path)).hexdigest(), checksum)
            self.assertEqual(len(archive.namelist()), len(record['files']))

    def test_changed_staging_is_rejected_before_any_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = self.fixture(root)
            name = next(iter(record['files']))
            (root / 'cloud/eppo' / record['artifact'] / record['version'] / name).write_bytes(b'changed')
            with patch.object(r, 'STAGING', root), patch.object(r.subprocess, 'run') as upload:
                with self.assertRaisesRegex(ValueError, 'Staged content differs'):
                    r.upload_record('v5.0.0', record)
                upload.assert_not_called()

    def test_failed_archive_upload_prevents_intent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = self.fixture(root)
            with patch.object(r, 'STAGING', root), patch.object(r.subprocess, 'run',
                    side_effect=subprocess.CalledProcessError(1, 'upload')) as upload:
                with self.assertRaises(subprocess.CalledProcessError):
                    r.upload_record('v5.0.0', record)
                self.assertEqual(len(upload.call_args_list), 1)
                self.assertTrue(upload.call_args.args[0][4].endswith('.zip'))

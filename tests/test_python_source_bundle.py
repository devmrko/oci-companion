import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
sys.path.insert(0, str(Path(__file__).parents[1] / 'src/main/resources/fn-python-template'))
import source_bundle


class SourceBundleTest(unittest.TestCase):
    def archive(self, extra=None):
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode='w:gz') as tar:
            content = json.dumps({'schema_version': 1, 'commit': 'a'*40}).encode()
            entry = tarfile.TarInfo('source.json'); entry.size = len(content)
            tar.addfile(entry, io.BytesIO(content))
            if extra:
                entry = tarfile.TarInfo(extra)
                if extra == 'link':
                    entry.type = tarfile.SYMTYPE; entry.linkname = '/etc/passwd'
                tar.addfile(entry, io.BytesIO(b''))
        return output.getvalue()

    def execute(self, content, root, change=None):
        request = {'commit': 'a'*40, 'sourceBundle': {'object': 'sources/test.tgz', 'version': 'v1', 'sha256': hashlib.sha256(content).hexdigest()}}
        if change: change(request)
        raw = Mock(); raw.stream.return_value = [content]
        storage = Mock(); storage.get_object.return_value = SimpleNamespace(data=SimpleNamespace(raw=raw))
        env = {'BUILD_SOURCE_NAMESPACE': 'test', 'BUILD_SOURCE_BUCKET': 'private-input', 'BUILD_SOURCE_PREFIX': 'sources/'}
        with patch.dict('os.environ', env):
            result = source_bundle.fetch(storage, request, root)
        storage.get_object.assert_called_once_with('test', 'private-input', 'sources/test.tgz', version_id='v1')
        raw.close.assert_called_once()
        return result

    def test_version_hash_manifest_and_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = self.execute(self.archive('project.py'), Path(directory))
            self.assertTrue((output / 'project.py').is_file())

    def test_invalid_inputs_never_extract(self):
        for change in (lambda r: r['sourceBundle'].update(sha256='b'*64),
                       lambda r: r.update(commit='c'*40),
                       lambda r: r['sourceBundle'].update(object='other/test.tgz'),
                       lambda r: r['sourceBundle'].update(version='')):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError): self.execute(self.archive(), Path(directory), change)
                self.assertFalse((Path(directory) / 'source').exists())

    def test_traversal_and_links_rejected_before_extraction(self):
        for name in ('../outside', '/absolute', 'link', 'source.json'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError): self.execute(self.archive(name), Path(directory))
                self.assertFalse((Path(directory) / 'source').exists())

    def test_size_limit(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict('os.environ', {'BUILD_SOURCE_MAX_BYTES': '1'}):
            with self.assertRaises(ValueError): self.execute(self.archive(), Path(directory))
            self.assertFalse((Path(directory) / 'source').exists())

import base64
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path[:0] = [str(ROOT / 'src/main/resources/fn-image-template'), str(ROOT / 'src/main/resources/fn-python-template')]
import image_layer


class ImageLayerTest(unittest.TestCase):
    def test_source_manifest_and_traversal_are_checked_before_extraction(self):
        for name, commit, valid in [('src/main.py', 'a' * 40, True), ('../escape', 'a' * 40, False), ('src/main.py', 'b' * 40, False)]:
            buffer = io.BytesIO()
            with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
                for path, content in [('source.json', json.dumps({'schema_version': 1, 'commit': commit}).encode()), (name, b'print(1)')]:
                    entry = tarfile.TarInfo(path); entry.size = len(content)
                    archive.addfile(entry, io.BytesIO(content))
            raw = buffer.getvalue()
            request = {'commit': 'a' * 40, 'sourceArchive': base64.b64encode(raw).decode(), 'sourceSha256': hashlib.sha256(raw).hexdigest()}
            with tempfile.TemporaryDirectory() as tmp:
                if valid:
                    self.assertTrue((image_layer.source(request, Path(tmp)) / name).is_file())
                else:
                    with self.assertRaises(ValueError): image_layer.source(request, Path(tmp))

    def test_layer_ownership_and_link_rejection(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); site = root / 'site'; site.mkdir(); (site / 'app.py').write_text('print(1)')
            image_layer.layer(site, root / 'layer.tgz')
            with tarfile.open(root / 'layer.tgz') as archive:
                info = archive.getmembers()[0]
                self.assertEqual(('app/site-packages/app.py', 10001, 10001), (info.name, info.uid, info.gid))
            (site / 'unsafe').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError): image_layer.layer(site, root / 'layer.tgz')

    def test_auth_is_host_bound_not_in_arguments_and_removed_on_failure(self):
        for fail in (False, True):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); calls = []
                def command(args, cwd, env):
                    calls.append(args)
                    self.assertNotIn('private-token', repr(args))
                    if args[1] == 'config': return json.dumps({'architecture': 'arm64', 'os': 'linux'})
                    config = json.loads((Path(env['DOCKER_CONFIG']) / 'config.json').read_text())
                    self.assertEqual(['region.ocir.io'], list(config['auths']))
                    if fail: raise RuntimeError('registry rejected')
                    return 'sha256:' + 'a' * 64 if args[1] == 'digest' else ''
                args = ({'tag': 'test', 'registryCredentials': {'username': 'user', 'password': 'private-token'}}, root, root / 'layer.tgz', command, 'python@sha256:' + 'b' * 64, 'region.ocir.io/ns/verify')
                if fail:
                    with self.assertRaises(RuntimeError): image_layer.publish(*args)
                else:
                    self.assertTrue(image_layer.publish(*args)['image'].endswith('a' * 64))
                self.assertFalse((root / 'registry-auth').exists())

    def test_registry_source_rejects_wrong_repo_and_removes_auth_before_extracting(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            request = {'sourceImage': 'region.ocir.io/ns/source@sha256:' + 'a' * 64,
                       'commit': 'b' * 40, 'sourceSha256': 'c' * 64,
                       'registryCredentials': {'username': 'user', 'password': 'secret'}}
            def command(args, cwd, env):
                self.assertTrue((Path(env['DOCKER_CONFIG']) / 'config.json').exists())
                with tarfile.open(args[-1], 'w') as archive:
                    entry = tarfile.TarInfo('../escape'); entry.size = 1
                    archive.addfile(entry, io.BytesIO(b'x'))
                return ''
            with self.assertRaises(ValueError):
                image_layer.registry_source(request, root, command, 'region.ocir.io/ns/other')
            with self.assertRaises(ValueError):
                image_layer.registry_source(request, root, command, 'region.ocir.io/ns/source')
            self.assertFalse((root / 'registry-auth').exists())
            self.assertFalse((root / 'source').exists())

    def test_wrong_architecture_and_unpinned_base_never_authenticate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for base in ('python:3.13', 'python@sha256:' + 'a' * 64):
                with self.assertRaises(ValueError):
                    image_layer.publish({'tag': 'x'}, root, root / 'layer', lambda *args: '{"architecture":"amd64","os":"linux"}', base, 'region.ocir.io/ns/test')
            self.assertFalse((root / 'registry-auth').exists())


if __name__ == '__main__': unittest.main()

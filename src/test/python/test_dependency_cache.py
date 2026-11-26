import importlib.util
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'src/main/resources/fn-image-template'))
import dependency_cache as cache

class DependencyCacheTest(unittest.TestCase):
    repo = 'region.ocir.io/ns/builder'
    base = 'public.example/python@sha256:' + 'a' * 64
    runtime = repo + '@sha256:' + 'b' * 64
    request = {'registryCredentials': {'username': 'user', 'password': 'not-a-real-token'}}

    def test_keys_change_with_lock_and_runtime_base(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / 'uv.lock').write_text('lock1')
            first = cache.identity(root, self.base, self.runtime, self.repo)
            self.assertEqual(first, cache.identity(root, self.base, self.runtime, self.repo))
            (root / 'uv.lock').write_text('lock2')
            self.assertNotEqual(first['app'], cache.identity(root, self.base, self.runtime, self.repo)['app'])
            (root / 'uv.lock').write_text('lock1')
            changed = cache.identity(root, self.base, self.repo + '@sha256:' + 'c' * 64, self.repo)
            self.assertEqual(first['app'], changed['app']); self.assertNotEqual(first['runtime'], changed['runtime'])

    def test_cache_hit_never_installs_and_auth_failures_are_not_cache_misses(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / 'uv.lock').write_text('lock')
            with patch.object(cache, 'cached', return_value=self.repo + '@sha256:' + 'c' * 64):
                def no_install(*args): raise AssertionError('No command expected on cache hit')
                result = cache.prepare(self.request, root, root, no_install, self.base, self.runtime, self.repo)
                self.assertTrue(result['cacheHit']); self.assertEqual('RUNTIME_REQUIRED', result['status'])
            for message, missing in [('MANIFEST_UNKNOWN', True), ('UNAUTHORIZED', False), ('network timeout', False)]:
                def error(*args): raise RuntimeError(message)
                if missing: self.assertIsNone(cache.cached(self.request, root, error, self.repo, self.repo + ':tag', 'lock'))
                else:
                    with self.assertRaises(RuntimeError): cache.cached(self.request, root, error, self.repo, self.repo + ':tag', 'lock')
                self.assertFalse((root / 'registry-auth').exists())

    def test_changed_lock_builds_runtime_and_application_layers_without_auth_in_install(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / 'uv.lock').write_text('changed-lock'); calls = []
            def command(args, cwd, env):
                self.assertNotIn('DOCKER_CONFIG', env); self.assertFalse((root / 'registry-auth').exists())
                site = Path(env['UV_PROJECT_ENVIRONMENT']) / 'lib/python3.13/site-packages'; site.mkdir(parents=True, exist_ok=True)
                (site / 'module.so').write_bytes(b'wheel-bytes'); calls.append(args)
            with patch.object(cache, 'cached', return_value=None), patch.object(cache, 'publish', return_value=self.repo + '@sha256:' + 'c' * 64) as publish:
                result = cache.prepare(self.request, root, root, command, self.base, self.runtime, self.repo)
                self.assertFalse(result['cacheHit']); self.assertEqual(2, publish.call_count)
                self.assertEqual(2, len(calls)); self.assertIn('--no-dev', calls[1])

    def test_runtime_layer_uses_hash_directory_and_rejects_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); site = root / 'site'; site.mkdir(); (site / 'extension.so').write_bytes(b'data'); lock = root / 'uv.lock'; lock.write_text('lock')
            cache.runtime_layer(site, lock, root / 'layer.tgz', '/opt/companion-deps/hash')
            with tarfile.open(root / 'layer.tgz') as archive:
                self.assertEqual({'opt/companion-deps/hash/site-packages/extension.so', 'opt/companion-deps/hash/uv.lock'}, set(archive.getnames()))
                self.assertTrue(all(x.uid == 10001 for x in archive.getmembers()))
            (site / 'bad').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError): cache.runtime_layer(site, lock, root / 'layer.tgz', '/opt/companion-deps/hash')

if __name__ == '__main__': unittest.main()

import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).parents[1] / 'src/main/resources/fn-python-template'


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class PythonBuildTest(unittest.TestCase):
    def test_tests_finish_before_wheel_build_and_commands_use_venv(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            (root / 'requirements-build.lock').write_text('pytest==9.2.0\nsetuptools==80.9.0\n')
            calls = []
            load('python_build').build(root, root, 'verify', lambda args, cwd: calls.append(args))
            self.assertEqual(len(calls), 6)
            self.assertIn('pytest', calls[4])
            self.assertIn('wheel', calls[5])
            self.assertTrue(all(c[0] == str(root / 'venv/bin/python') for c in calls[1:]))

    def test_invalid_lock_runs_no_commands(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            (root / 'requirements-build.lock').write_text('--extra-index-url https://example.invalid\n')
            command = Mock()
            with self.assertRaises(ValueError):
                load('python_build').build(root, root, 'verify', command)
            command.assert_not_called()

    def test_test_failure_prevents_wheel(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            (root / 'requirements-build.lock').write_text('pytest==9.2.0\n')
            calls = []
            def command(args, cwd):
                calls.append(args)
                if 'pytest' in args:
                    raise RuntimeError('test failed')
            with self.assertRaises(RuntimeError):
                load('python_build').build(root, root, 'verify', command)
            self.assertFalse(any('wheel' in c for c in calls))

    def test_handler_capacity_failure_is_recorded_before_git(self):
        storage = Mock()
        oci = types.ModuleType('oci')
        oci.auth = types.SimpleNamespace(signers=types.SimpleNamespace(get_resource_principals_signer=lambda: object()))
        oci.object_storage = types.SimpleNamespace(ObjectStorageClient=lambda *a, **kw: storage)
        oci.exceptions = types.SimpleNamespace(ServiceError=type('ServiceError', (Exception,), {}))
        fdk = types.ModuleType('fdk')
        fdk.response = types.SimpleNamespace(Response=lambda ctx, **kw: kw)
        request = {'action': 'build', 'runId': '00000000-0000-4000-8000-000000000001',
                   'source': 'https://github.com/example/project.git', 'commit': 'a'*40,
                   'goal': 'verify', 'requiredFreeBytes': 10**18}
        with patch.dict(sys.modules, {'oci': oci, 'fdk': fdk}), patch.dict('os.environ', {
                'BUILD_NAMESPACE': 'test', 'BUILD_BUCKET': 'test-results', 'BUILD_PREFIX': 'python-test',
                'BUILD_MIN_FREE_BYTES': '1024'}):
            handler = load('func')
            with patch.object(handler.subprocess, 'Popen') as process:
                response = handler.handler(None, io.BytesIO(json.dumps(request).encode()))
                process.assert_not_called()
        result = json.loads(response['response_data'])
        self.assertEqual(result['status'], 'FAILED')
        self.assertIn('capacity', result['error'])
        self.assertEqual(response['status_code'], 500)
        first = storage.put_object.call_args_list[0]
        self.assertEqual(first.kwargs['if_none_match'], '*')
        last = json.loads(storage.put_object.call_args_list[-1].args[3])
        self.assertEqual(last['status'], 'FAILED')


if __name__ == '__main__':
    unittest.main()

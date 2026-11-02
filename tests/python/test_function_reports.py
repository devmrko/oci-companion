"""Failure-path contract test; external OCI and process boundaries are replaced only here."""
import importlib.util
import io
import json
import pathlib
import sys
import tarfile
import types
import unittest
from unittest.mock import patch

class ReportsTest(unittest.TestCase):
    def test_failed_maven_still_publishes_reports_and_failure_status(self):
        objects = {}
        class Storage:
            def put_object(self, namespace, bucket, key, body, **kwargs):
                objects[key] = body.read() if hasattr(body, 'read') else body
        oci = types.SimpleNamespace(auth=types.SimpleNamespace(signers=types.SimpleNamespace(get_resource_principals_signer=lambda: object())), object_storage=types.SimpleNamespace(ObjectStorageClient=lambda *a, **k: Storage()), exceptions=types.SimpleNamespace(ServiceError=type('ServiceError', (Exception,), {})))
        fdk = types.SimpleNamespace(response=types.SimpleNamespace(Response=lambda ctx, **kwargs: kwargs))
        with patch.dict(sys.modules, {'oci': oci, 'fdk': fdk}), patch.dict('os.environ', {'BUILD_NAMESPACE': 'namespace', 'BUILD_BUCKET': 'bucket', 'BUILD_PREFIX': 'test-builder'}):
            spec = importlib.util.spec_from_file_location('function_reports_fixture', 'src/main/resources/fn-template/func.py')
            module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
            class Process:
                def __init__(self, args, cwd, **kwargs):
                    self.returncode = 0
                    if args[:2] == ['git', 'init']:
                        (pathlib.Path(cwd) / 'source').mkdir()
                    if args[0] == 'mvn':
                        path = pathlib.Path(cwd) / 'target' / 'surefire-reports'; path.mkdir(parents=True)
                        (path / 'TEST.xml').write_text('<testsuite><testcase name="failed"><failure>expected</failure></testcase></testsuite>')
                        (path.parent / 'pmd.xml').write_text('<pmd version="7.17.0"><file name="Example.java"><violation rule="Rule" beginline="1">issue</violation></file></pmd>')
                        self.returncode = 1
                def poll(self): return self.returncode
            request = {'action': 'build', 'runId': '12345678-1234-1234-1234-123456789abc', 'source': 'https://github.com/example/project', 'commit': 'a'*40, 'goal': 'test'}
            with patch.object(module.subprocess, 'Popen', Process), patch.object(module.subprocess, 'check_output', return_value='a'*40):
                result = module.handler(None, io.BytesIO(json.dumps(request).encode()))
            self.assertEqual(500, result['status_code'])
            result = json.loads(result['response_data']); self.assertEqual('FAILED', result['status'])
            archive = objects[result['testReports']]
            with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as reports:
                self.assertEqual({'target/surefire-reports/TEST.xml', 'target/pmd.xml'}, set(reports.getnames()))
            stored = json.loads(objects['test-builder/'+request['runId']+'/result.json'])
            self.assertEqual('FAILED', stored['status'])

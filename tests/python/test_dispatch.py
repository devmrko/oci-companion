"""Isolated orchestration tests; production template always calls the real OCI SDK."""
import importlib.util
import json
import os
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

# The HTTP response wrapper is irrelevant to dispatch policy; keep SDK models real.
sys.modules['fdk'] = types.SimpleNamespace(response=types.SimpleNamespace(Response=lambda ctx, **kw: kw))
spec = importlib.util.spec_from_file_location('dispatch', Path(__file__).resolve().parents[2] / 'src/main/resources/dispatch-template/func.py')
dispatch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dispatch)

class DispatchTest(unittest.TestCase):
    def run_handler(self, client):
        with patch.dict(os.environ, {'BUILD_PIPELINE_ID': 'ocid1.devopsbuildpipeline.oc1.test', 'BUILD_REGION': 'ap-seoul-1'}), patch.object(dispatch.oci.auth.signers, 'get_resource_principals_signer', return_value=Mock()), patch.object(dispatch.oci.devops, 'DevopsClient', return_value=client):
            return json.loads(dispatch.handler(Mock(CallID=Mock(return_value='same-call-id')))['response_data'])

    def test_active_build_is_skipped_without_submission(self):
        client = Mock()
        client.list_build_runs.return_value.data.items = [types.SimpleNamespace(id='active-run')]
        self.assertEqual('SKIPPED', self.run_handler(client)['status'])
        client.create_build_run.assert_not_called()

    def test_submission_uses_pipeline_defaults_and_stable_token(self):
        client = Mock()
        client.list_build_runs.return_value.data.items = []
        client.create_build_run.return_value.data.id = 'new-run'
        self.assertEqual('SUBMITTED', self.run_handler(client)['status'])
        first = client.create_build_run.call_args
        self.run_handler(client)
        self.assertEqual(first.kwargs['opc_retry_token'], client.create_build_run.call_args.kwargs['opc_retry_token'])
        model = first.args[0]
        self.assertIsNone(model.commit_info)
        self.assertIsNone(model.build_run_arguments)
        self.assertEqual('ocid1.devopsbuildpipeline.oc1.test', model.build_pipeline_id)

    def test_unavailable_overlap_lookup_does_not_submit(self):
        client = Mock()
        client.list_build_runs.side_effect = dispatch.oci.exceptions.ServiceError(403, 'NotAuthorized', {}, 'denied')
        with self.assertRaises(dispatch.oci.exceptions.ServiceError):
            self.run_handler(client)
        client.create_build_run.assert_not_called()

if __name__ == '__main__':
    unittest.main()

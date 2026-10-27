"""Standalone OCI Resource Scheduler -> DevOps bridge. No Companion dependency."""
import hashlib
import json
import logging
import os

import oci
from fdk import response


def handler(ctx, data=None):
    pipeline = os.environ['BUILD_PIPELINE_ID']
    region = os.environ['BUILD_REGION']
    signer = oci.auth.signers.get_resource_principals_signer()
    client = oci.devops.DevopsClient({'region': region}, signer=signer,
                                   retry_strategy=oci.retry.NoneRetryStrategy(), timeout=(5, 30))
    try:
        # Best-effort overlap check. Independent schedules can race; not a distributed lock.
        for state in ('ACCEPTED', 'IN_PROGRESS', 'CANCELING'):
            runs = client.list_build_runs(build_pipeline_id=pipeline, lifecycle_state=state, limit=1).data.items
            if runs:
                result = {'status': 'SKIPPED', 'reason': 'build-active', 'buildRunId': runs[0].id}
                logging.info(json.dumps(result))
                return response.Response(ctx, response_data=json.dumps(result), headers={'Content-Type': 'application/json'})
        # Stable retry token within this Fn invocation; no blind SDK retry on an uncertain submission.
        token = hashlib.sha256((pipeline + ':' + ctx.CallID()).encode()).hexdigest()
        run = client.create_build_run(oci.devops.models.CreateBuildRunDetails(
            build_pipeline_id=pipeline, display_name='scheduled-' + token[:12]), opc_retry_token=token).data
        result = {'status': 'SUBMITTED', 'buildRunId': run.id}
        logging.info(json.dumps(result))
        return response.Response(ctx, response_data=json.dumps(result), headers={'Content-Type': 'application/json'})
    except oci.exceptions.ServiceError as error:
        # No exception body, headers, credentials or payload in the log.
        logging.error('DevOps request failed status=%s code=%s; inspect build history before retry', error.status, error.code)
        raise

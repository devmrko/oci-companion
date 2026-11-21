"""One read-only OCI call. Never print tokens, exception text or object names."""
import json
import os
import oci

result = {"schema": 1, "operation": "ListObjects", "state": "AUTH_FAILURE", "httpStatus": 0}
try:
    signer = oci.auth.signers.get_oke_workload_identity_resource_principal_signer()
    client = oci.object_storage.ObjectStorageClient(
        config={"region": os.environ["OCI_RESOURCE_PRINCIPAL_REGION"]}, signer=signer,
        timeout=(5, 10), retry_strategy=oci.retry.NoneRetryStrategy())
    try:
        response = client.list_objects(os.environ["OBJECT_NAMESPACE"], os.environ["BUCKET"], limit=1)
        result.update(state="GRANTED", httpStatus=response.status)
        del response
    except oci.exceptions.ServiceError as failure:
        result.update(state="DENIED_OR_NOT_FOUND" if failure.status in (401, 403, 404) else "API_FAILURE", httpStatus=failure.status)
    except Exception:
        result.update(state="CONNECTION_FAILURE")
except Exception:
    pass
print(json.dumps(result), flush=True)

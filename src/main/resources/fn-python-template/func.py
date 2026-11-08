"""Standalone Python Function builder. Contract: python-v1; no companion app dependency."""
import hashlib
import json
import os
import pathlib
import platform
import shutil
import subprocess
import tempfile
import time
import uuid
import re
from urllib.parse import urlparse

import oci
from fdk import response

CONTRACT = "python-v1"

def validate(request):
    source = request.get("source", "")
    uri = urlparse(source)
    hosts = os.environ.get("BUILD_ALLOWED_GIT_HOSTS", "github.com,gitlab.com").split(",")
    if uri.scheme != "https" or uri.hostname not in hosts or uri.username or uri.password or uri.query or uri.fragment or uri.port not in (None, 443):
        raise ValueError("Source must be an allowed HTTPS Git repository without credentials")
    if not re.fullmatch(r"[0-9a-f]{40}", request.get("commit", "")):
        raise ValueError("A full Git commit SHA is required")
    uuid.UUID(request["runId"])
    if request.get("goal") not in ("test", "package", "verify"):
        raise ValueError("Goal must be test, package or verify")
    if request.get("profiles", ""):
        raise ValueError("Python builds do not accept Maven profiles")
    if type(request.get("requiredFreeBytes")) is not int or request["requiredFreeBytes"] <= 0:
        raise ValueError("Positive requiredFreeBytes capacity budget is required")
    for field in ("directory", "artifact"):
        value = request.get(field, "." if field == "directory" else "")
        if len(value) > 250 or value.startswith("/") or ".." in value.split("/") or "\\" in value or any(ord(x) < 32 for x in value):
            raise ValueError("Paths must remain within source directory")
    return request


def memory():
    values = {}
    for name in ("memory.current", "memory.peak", "memory.max", "memory/memory.usage_in_bytes",
                 "memory/memory.max_usage_in_bytes", "memory/memory.limit_in_bytes"):
        path = pathlib.Path("/sys/fs/cgroup") / name
        if path.is_file():
            values[name] = path.read_text().strip()
    return values


def handler(ctx, data=None):
    request = json.loads(data.getvalue() or b"{}") if data else {}
    probe = {"contract": CONTRACT, "arch": platform.machine(), "tmp": dict(zip(("total", "used", "free"), shutil.disk_usage("/tmp"))),
             "memory": memory(), "tools": {x: shutil.which(x) for x in ("python3", "git")}}
    if request.get("action", "probe") == "probe":
        return response.Response(ctx, response_data=json.dumps(probe), headers={"Content-Type": "application/json"})
    if request.get("action") != "build":
        raise ValueError("Expected action=build")
    validate(request)
    signer = oci.auth.signers.get_resource_principals_signer()
    storage = oci.object_storage.ObjectStorageClient({}, signer=signer)
    namespace, bucket = os.environ["BUILD_NAMESPACE"], os.environ["BUILD_BUCKET"]
    run = str(uuid.UUID(request["runId"]))
    prefix = os.environ["BUILD_PREFIX"]
    if not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", prefix):
        raise ValueError("Invalid BUILD_PREFIX")
    key = prefix + "/" + run
    started = time.monotonic()
    result = {"run": run, "source": request["source"], "commit": request["commit"], "goal": request["goal"], "environment": probe, "status": "RUNNING"}
    def put(name, content):
        storage.put_object(namespace, bucket, key + "/" + name, content)
    def status():
        result.update(elapsedSeconds=round(time.monotonic() - started, 2), memory=memory())
        put("result.json", json.dumps(result).encode())
    try:
        storage.put_object(namespace, bucket, key + "/result.json", json.dumps(result).encode(), if_none_match="*")
    except oci.exceptions.ServiceError as error:
        if error.status == 412:
            return response.Response(ctx, response_data=json.dumps({"run": run, "status": "ALREADY_ACCEPTED"}))
        raise
    with tempfile.TemporaryDirectory(prefix="companion-build-") as work:
        root = pathlib.Path(work)
        logpath = root / "build.log"
        env = dict(os.environ, HOME=work, PIP_NO_CACHE_DIR="1", PYTHONDONTWRITEBYTECODE="1")
        def command(args, cwd):
            print(json.dumps({"run": run, "command": args}), flush=True)
            with logpath.open("ab", buffering=0) as log:
                log.write(("\n$ " + " ".join(args) + "\n").encode())
                process = subprocess.Popen(args, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT,
                                           start_new_session=True)
                try:
                    while process.poll() is None:
                        if time.monotonic() - started > 3300:
                            raise TimeoutError("Build exceeded 3300 seconds")
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            pass
                        status()
                        with logpath.open("rb") as progress:
                            put("build.log", progress)
                    if process.returncode:
                        raise RuntimeError("Command exited " + str(process.returncode))
                finally:
                    if process.poll() is None:
                        import signal
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
            with logpath.open("rb") as log:
                put("build.log", log)
        directory = None
        try:
            minimum = int(os.environ["BUILD_MIN_FREE_BYTES"])
            if minimum <= 0:
                raise ValueError("Administrator capacity minimum must be positive")
            required = max(minimum, request["requiredFreeBytes"])
            result["capacity"] = {"requiredFreeBytes": required, "availableBytes": shutil.disk_usage(root).free}
            if result["capacity"]["availableBytes"] < required:
                raise ValueError("Insufficient Function temporary capacity")
            command(["git", "init", "source"], root)
            source = root / "source"
            command(["git", "remote", "add", "origin", request["source"]], source)
            command(["git", "fetch", "--depth", "1", "origin", request["commit"]], source)
            command(["git", "checkout", "--detach", "FETCH_HEAD"], source)
            actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
            if actual != request["commit"]:
                raise RuntimeError("Source revision mismatch")
            directory = (source / request.get("directory", ".")).resolve()
            if not directory.is_relative_to(source.resolve()) or not directory.is_dir():
                raise ValueError("Work directory is outside source or missing")
            from python_build import build
            build(directory, root, request["goal"], command)
            artifacts = []
            pattern = request.get("artifact", "")
            if pattern:
                matches = list(directory.glob(pattern))
                if not matches or len(matches) > 20:
                    raise ValueError("Expected 1 to 20 artifact files")
                for jar in matches:
                    if not jar.is_file() or not jar.resolve().is_relative_to(source.resolve()):
                        raise ValueError("Invalid artifact file")
                    relative = str(jar.relative_to(directory))
                    with jar.open("rb") as content:
                        digest = hashlib.file_digest(content, "sha256").hexdigest()
                    with jar.open("rb") as content:
                        put("artifacts/" + relative, content)
                    artifacts.append({"object": key + "/artifacts/" + relative, "bytes": jar.stat().st_size, "sha256": digest})
            result.update(status="SUCCEEDED", artifacts=artifacts)
        except Exception as error:
            result.update(status="FAILED", error=type(error).__name__ + ": " + str(error))
        finally:
            # Preserve test evidence even when pytest returns a non-zero status.
            try:
                if directory is not None and directory.is_dir():
                    import tarfile
                    files = [p for p in directory.rglob("*.xml")
                             if (any(x in ("python-test-reports",) for x in p.parts) or p.name == "pmd.xml")
                             and p.is_file() and not p.is_symlink()
                             and p.resolve().is_relative_to(directory)]
                    if files:
                        archive = root / "test-reports.tar.gz"
                        with tarfile.open(archive, "w:gz") as reports:
                            for path in files:
                                reports.add(path, arcname=str(path.relative_to(directory)), recursive=False)
                        with archive.open("rb") as content:
                            put("test-reports.tar.gz", content)
                        result["testReports"] = key + "/test-reports.tar.gz"
            except Exception as error:
                result["reportError"] = type(error).__name__ + ": " + str(error)
                result["status"] = "FAILED"
            if logpath.exists():
                with logpath.open("rb") as log:
                    put("build.log", log)
            result["workspaceBytes"] = sum(p.stat().st_size for p in root.rglob("*") if p.is_file())
            status()
        print(json.dumps(result), flush=True)
        return response.Response(ctx, response_data=json.dumps(result), headers={"Content-Type": "application/json"},
                                 status_code=200 if result["status"] == "SUCCEEDED" else 500)

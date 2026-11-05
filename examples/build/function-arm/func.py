"""Fixed-source ARM build experiment. No caller-supplied shell commands or credentials."""
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

import oci
from fdk import response

SOURCE = "https://github.com/devmrko/db-companion.git"
COMMIT = "7a5cb34b68d8dae90f9c398667a0db976bb2f6d2"


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
    probe = {"arch": platform.machine(), "tmp": dict(zip(("total", "used", "free"), shutil.disk_usage("/tmp"))),
             "memory": memory(), "tools": {x: shutil.which(x) for x in ("java", "javac", "mvn", "git")}}
    if request.get("action", "probe") == "probe":
        return response.Response(ctx, response_data=json.dumps(probe), headers={"Content-Type": "application/json"})
    if request.get("action") != "build" or probe["arch"] != "aarch64":
        raise ValueError("Expected action=build on aarch64")
    signer = oci.auth.signers.get_resource_principals_signer()
    storage = oci.object_storage.ObjectStorageClient({}, signer=signer)
    namespace, bucket = os.environ["BUILD_NAMESPACE"], os.environ["BUILD_BUCKET"]
    run = "function-arm-" + str(uuid.uuid4())
    started = time.monotonic()
    result = {"run": run, "source": SOURCE, "commit": COMMIT, "environment": probe, "status": "RUNNING"}
    def put(name, content):
        storage.put_object(namespace, bucket, run + "/" + name, content)
    def status():
        result.update(elapsedSeconds=round(time.monotonic() - started, 2), memory=memory())
        put("result.json", json.dumps(result).encode())
    status()
    with tempfile.TemporaryDirectory(prefix="companion-build-") as work:
        root = pathlib.Path(work)
        logpath = root / "build.log"
        env = dict(os.environ, HOME=work, MAVEN_OPTS="-Xmx768m -XX:MaxMetaspaceSize=256m")
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
                        time.sleep(5)
                        status()
                    if process.returncode:
                        raise RuntimeError("Command exited " + str(process.returncode))
                finally:
                    if process.poll() is None:
                        import signal
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
            with logpath.open("rb") as log:
                put("build.log", log)
        try:
            command(["git", "init", "source"], root)
            source = root / "source"
            command(["git", "remote", "add", "origin", SOURCE], source)
            command(["git", "fetch", "--depth", "1", "origin", COMMIT], source)
            command(["git", "checkout", "--detach", "FETCH_HEAD"], source)
            actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
            if actual != COMMIT:
                raise RuntimeError("Source revision mismatch")
            command(["mvn", "-B", "-ntp", "-Pquality", "-Dmaven.repo.local=" + work + "/m2",
                     "-DargLine=-Xmx768m", "clean", "verify"], source)
            jars = list((source / "target").glob("db-manage-companion-*.jar"))
            if len(jars) != 1:
                raise RuntimeError("Expected exactly one output JAR")
            jar = jars[0]
            digest = hashlib.file_digest(jar.open("rb"), "sha256").hexdigest()
            with jar.open("rb") as content:
                put("db-companion.jar", content)
            command(["tar", "czf", str(root / "test-reports.tar.gz"), "target/surefire-reports", "target/pmd.xml"], source)
            with (root / "test-reports.tar.gz").open("rb") as content:
                put("test-reports.tar.gz", content)
            result.update(status="SUCCEEDED", artifact={"object": run + "/db-companion.jar",
                          "bytes": jar.stat().st_size, "sha256": digest}, scope="Maven quality verify; JavaScript tests excluded")
        except Exception as error:
            result.update(status="FAILED", error=type(error).__name__ + ": " + str(error))
        finally:
            if logpath.exists():
                with logpath.open("rb") as log:
                    put("build.log", log)
            result["workspaceBytes"] = sum(p.stat().st_size for p in root.rglob("*") if p.is_file())
            status()
        print(json.dumps(result), flush=True)
        return response.Response(ctx, response_data=json.dumps(result), headers={"Content-Type": "application/json"},
                                 status_code=200 if result["status"] == "SUCCEEDED" else 500)

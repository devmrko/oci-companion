"""Synchronous ARM image experiment; no Docker socket, daemon or privileged operations."""
import json
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

from fdk import response
import image_layer


def handler(ctx, data=None):
    request = json.loads(data.getvalue() or b'{}') if data else {}
    started = time.monotonic()
    probe = {'contract': 'sustena-layer-v1', 'arch': platform.machine(), 'uid': os.getuid(),
             'python': platform.python_version(), 'tmpFreeBytes': shutil.disk_usage('/tmp').free,
             'tools': {name: bool(shutil.which(name)) for name in ('uv', 'crane', 'docker', 'podman')}}
    def reply(value):
        return response.Response(ctx, response_data=json.dumps(value), headers={'Content-Type': 'application/json'})
    if request.get('action', 'probe') == 'probe':
        return reply(probe)
    if request.get('action') != 'build' or probe['arch'] != 'aarch64' or sys.version_info[:2] != (3, 13):
        return reply({'status': 'FAILED', 'error': 'Expected build on aarch64 Python 3.13', 'environment': probe})
    result = {'status': 'RUNNING', 'environment': probe, 'commit': request.get('commit'), 'phase': 'source'}
    def safe(message):
        text = str(message)[-8000:]
        for value in request.get('registryCredentials', {}).values():
            if isinstance(value, str) and value:
                text = text.replace(value, '[REDACTED]')
        return re.sub(r'ocid1\.[\w.-]+', '[OCID]', text)
    with tempfile.TemporaryDirectory(prefix='companion-image-') as work:
        root = Path(work)
        env = dict(os.environ, HOME=work, TMPDIR=work, UV_CACHE_DIR=work + '/cache', UV_PYTHON_DOWNLOADS='never',
                   UV_LINK_MODE='copy', PYTHONDONTWRITEBYTECODE='1')
        def command(args, cwd, extra=None):
            remaining = 270 - (time.monotonic() - started)
            if remaining <= 0:
                raise TimeoutError('Function build budget exceeded')
            with tempfile.TemporaryFile() as out:
                process = subprocess.Popen(args, cwd=cwd, env=dict(env, **(extra or {})),
                                           stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    code = process.wait(timeout=remaining)
                finally:
                    if process.poll() is None:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                size = out.tell()
                out.seek(max(0, size - 16000))
                text = out.read().decode(errors='replace')
            if code:
                raise RuntimeError(f'{args[0]} exited {code}: {text}')
            return text
        try:
            source = (image_layer.registry_source(request, root, command, os.environ['SOURCE_REPOSITORY'])
                      if 'sourceImage' in request else image_layer.source(request, root))
            if not (source / 'uv.lock').is_file() or not (source / 'src/sustena').is_dir():
                raise ValueError('Sustena source with uv.lock is required')
            if hashlib.sha256((source / 'uv.lock').read_bytes()).digest() != hashlib.sha256(Path('/opt/build-deps/uv.lock').read_bytes()).digest():
                raise ValueError('Function dependency lock differs from source; rebuild the Function runtime')
            result['phase'] = 'dependencies'
            command(['uv', 'sync', '--frozen', '--no-editable'], source)
            result['phase'] = 'test'
            command(['/opt/build-deps/.venv/bin/python', '-m', 'pytest', '-q', '--junitxml=' + str(root / 'tests.xml')], source, {'PYTHONPATH': str(source / 'src')})
            tests = ET.parse(root / 'tests.xml').getroot()
            suites = [tests] if tests.tag == 'testsuite' else list(tests.iter('testsuite'))
            result['tests'] = {key: sum(int(s.get(key, '0')) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
            if result['tests']['tests'] == 0:
                raise ValueError('No tests executed')
            command(['uv', 'sync', '--frozen', '--no-dev', '--no-editable'], source)
            shutil.rmtree(root / 'cache', ignore_errors=True)
            result['phase'] = 'layer'
            archive = root / 'app-layer.tar.gz'
            result['layerBytes'] = image_layer.layer(source / '.venv/lib/python3.13/site-packages', archive)
            result['phase'] = 'push'
            result.update(image_layer.publish(request, root, archive, command,
                          os.environ['IMAGE_BASE'], os.environ['IMAGE_REPOSITORY']))
            result.update(status='SUCCEEDED', phase='complete')
        except Exception as error:
            result.update(status='FAILED', error=safe(error))
    result['elapsedSeconds'] = round(time.monotonic() - started, 2)
    return reply(result)

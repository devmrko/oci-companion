"""Lock-keyed dependency layers assembled in Functions; no executable files in /tmp required."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tarfile
import image_layer


def installed_lock():
    path = os.environ.get('COMPANION_DEPS', '')
    if not re.fullmatch(r'/opt/companion-deps/[a-f0-9]{64}', path):
        return ''
    lock = Path(path) / 'uv.lock'
    return hashlib.sha256(lock.read_bytes()).hexdigest() if lock.is_file() else ''


def identity(source, base, runtime_base, repository):
    for value in (base, runtime_base):
        if not re.fullmatch(r'[a-zA-Z0-9./:_-]+@sha256:[a-f0-9]{64}', value):
            raise ValueError('Digest-pinned dependency bases required')
    if not runtime_base.startswith(repository + '@'):
        raise ValueError('Runtime base must belong to configured source repository')
    lock = hashlib.sha256((source / 'uv.lock').read_bytes()).hexdigest()
    recipe = hashlib.sha256(('v2:linux/arm64:python3.13:' + lock + ':' + base).encode()).hexdigest()
    runtime = hashlib.sha256((recipe + ':' + runtime_base).encode()).hexdigest()
    return {'lock': lock, 'app': repository + ':deps-' + recipe,
            'runtime': repository + ':runtime-' + runtime, 'path': '/opt/companion-deps/' + lock}


def cached(request, root, command, repository, image, lock):
    with image_layer.registry_auth(request, root, repository) as auth:
        try:
            config = json.loads(command(['crane', 'config', image], root, auth))
            labels = config.get('config', {}).get('Labels', {})
            if labels.get('companion.lock') != lock or labels.get('companion.cache') != 'v2':
                raise ValueError('Dependency cache identity mismatch')
            digest = command(['crane', 'digest', image], root, auth).strip()
            if not re.fullmatch(r'sha256:[a-f0-9]{64}', digest):
                raise ValueError('Invalid cached digest')
            return repository + '@' + digest
        except RuntimeError as error:
            # Authentication/network errors must not be interpreted as an absent cache.
            if not any(code in str(error) for code in ('MANIFEST_UNKNOWN', 'NAME_UNKNOWN', '404 Not Found')):
                raise
            return None


def runtime_layer(site, lock_file, destination, prefix):
    with tarfile.open(destination, 'w:gz', format=tarfile.PAX_FORMAT) as out:
        files = [(p, prefix.lstrip('/') + '/site-packages/' + p.relative_to(site).as_posix())
                 for p in sorted(site.rglob('*')) if '__pycache__' not in p.parts and p.suffix != '.pyc']
        files.append((lock_file, prefix.lstrip('/') + '/uv.lock'))
        total = 0
        for path, name in files:
            if path.is_symlink() or not (path.is_dir() or path.is_file()):
                raise ValueError('Dependency layer contains a non-ordinary file')
            total += path.stat().st_size if path.is_file() else 0
            if total > 512 * 1024 * 1024:
                raise ValueError('Dependency layer exceeds limit')
            info = out.gettarinfo(str(path), name)
            info.uid = info.gid = 10001; info.uname = info.gname = ''; info.mtime = 0
            info.mode = 0o755 if path.is_dir() else 0o644
            if path.is_file():
                with path.open('rb') as stream: out.addfile(info, stream)
            else: out.addfile(info)


def publish(request, root, command, repository, base, layer, tag, lock, runtime_path=None):
    with image_layer.registry_auth(request, root, repository) as auth:
        command(['crane', 'append', '--platform=linux/arm64', '-b', base, '-f', str(layer), '-t', tag], root, auth)
        args = ['crane', 'mutate', tag, '--label=companion.lock=' + lock, '--label=companion.cache=v2', '--tag', tag]
        if runtime_path:
            args += ['--env=COMPANION_DEPS=' + runtime_path]
        command(args, root, auth)
        digest = command(['crane', 'digest', tag], root, auth).strip()
        if not re.fullmatch(r'sha256:[a-f0-9]{64}', digest):
            raise ValueError('Invalid dependency image digest')
        return repository + '@' + digest


def prepare(request, root, source, command, base, runtime_base, repository):
    key = identity(source, base, runtime_base, repository)
    app = cached(request, root, command, repository, key['app'], key['lock'])
    runtime = cached(request, root, command, repository, key['runtime'], key['lock'])
    hit = bool(app and runtime)
    if not hit:
        venv = root / 'dependency-environment'
        extra = {'UV_PROJECT_ENVIRONMENT': str(venv)}
        command(['uv', 'sync', '--locked', '--no-editable', '--no-install-project'], source, extra)
        site = venv / 'lib/python3.13/site-packages'
        layer = root / 'runtime-dependencies.tar.gz'
        if not runtime:
            runtime_layer(site, source / 'uv.lock', layer, key['path'])
            runtime = publish(request, root, command, repository, runtime_base, layer,
                              key['runtime'], key['lock'], key['path'])
            layer.unlink()
        if not app:
            command(['uv', 'sync', '--locked', '--no-editable', '--no-dev', '--no-install-project'], source, extra)
            image_layer.layer(site, layer)
            app = publish(request, root, command, repository, base, layer, key['app'], key['lock'])
            layer.unlink()
        shutil.rmtree(venv)
    return {'status': 'RUNTIME_REQUIRED', 'runtimeImage': runtime, 'runtimeTag': key['runtime'],
            'dependencyImage': app, 'lockHash': key['lock'], 'cacheHit': hit}

"""Daemonless image assembly for the initial, explicitly selected Sustena recipe."""
import base64
from contextlib import contextmanager
import shutil
import hashlib
import json
import os
from pathlib import Path
import re
import tarfile

MAX_SOURCE_BYTES = 4 * 1024 * 1024


def source(request, root):
    from source_bundle import extract
    if not re.fullmatch(r'[0-9a-f]{40}', request.get('commit', '')):
        raise ValueError('A full commit SHA is required')
    encoded = request.get('sourceArchive', '')
    if not isinstance(encoded, str) or len(encoded) > (MAX_SOURCE_BYTES + 2) // 3 * 4:
        raise ValueError('Source invocation limit exceeded')
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > MAX_SOURCE_BYTES or hashlib.sha256(raw).hexdigest() != request.get('sourceSha256'):
        raise ValueError('Source checksum or size mismatch')
    archive = root / 'source.tgz'
    archive.write_bytes(raw)
    return extract(archive, request, root)


def target(request, allowed_repository):
    if not re.fullmatch(r'[a-z0-9.-]+\.ocir\.io/[a-z0-9_-]+/[a-z0-9_./-]+', allowed_repository):
        raise ValueError('Configured OCIR repository required')
    if '..' in allowed_repository.split('/'):
        raise ValueError('Invalid OCIR repository')
    tag = request.get('tag', '')
    if not re.fullmatch(r'[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}', tag):
        raise ValueError('A bounded image tag is required')
    return allowed_repository + ':' + tag


def layer(site_packages, destination):
    """Normalize ownership and reject links/devices before writing a portable layer."""
    site_packages = Path(site_packages)
    files = sorted(site_packages.rglob('*'))
    total = 0
    with tarfile.open(destination, 'w:gz', format=tarfile.PAX_FORMAT) as archive:
        for path in files:
            if path.is_symlink() or not (path.is_file() or path.is_dir()):
                raise ValueError('Only ordinary installed files may enter the layer')
            if '__pycache__' in path.parts or path.suffix == '.pyc':
                continue
            total += path.stat().st_size if path.is_file() else 0
            if total > 512 * 1024 * 1024:
                raise ValueError('Installed layer size limit exceeded')
            name = 'app/site-packages/' + path.relative_to(site_packages).as_posix()
            info = archive.gettarinfo(str(path), arcname=name)
            info.uid = info.gid = 10001
            info.uname = info.gname = ''
            info.mode = 0o755 if path.is_dir() else 0o644
            info.mtime = 0
            if path.is_file():
                with path.open('rb') as stream:
                    archive.addfile(info, stream)
            else:
                archive.addfile(info)
    return total


def publish(request, root, archive, command, base, repository):
    if not re.fullmatch(r'[a-zA-Z0-9./:_-]+@sha256:[a-f0-9]{64}', base):
        raise ValueError('A digest-pinned base image is required')
    image = target(request, repository)
    with registry_auth(request, root, repository) as env:
        config = json.loads(command(['crane', 'config', '--platform=linux/arm64', base], root, env))
        if config.get('architecture') != 'arm64' or config.get('os') != 'linux':
            raise ValueError('Base image must be linux/arm64')
        command(['crane', 'append', '--platform=linux/arm64', '-b', base, '-f', str(archive), '-t', image], root, env)
        command(['crane', 'mutate', image, '--user=10001', '--workdir=/app',
                 '--env=PYTHONPATH=/app/site-packages', '--env=PYTHONUNBUFFERED=1',
                 '--env=SUSTENA_HOST=0.0.0.0', '--env=SUSTENA_PORT=8845',
                 '--entrypoint=python,-c,from sustena.cli import main; main()',
                 '--cmd=serve', '--tag', image], root, env)
        digest = command(['crane', 'digest', image], root, env).strip()
        if not re.fullmatch(r'sha256:[a-f0-9]{64}', digest):
            raise ValueError('Registry did not return an image digest')
        return {'image': repository + '@' + digest, 'digest': digest}


@contextmanager
def registry_auth(request, root, repository):
    credentials = request.get('registryCredentials', {})
    if set(credentials) != {'username', 'password'} or any(not isinstance(v, str) or not v for v in credentials.values()):
        raise ValueError('Registry credentials required')
    auth_dir = root / 'registry-auth'
    auth_dir.mkdir(mode=0o700)
    path = auth_dir / 'config.json'
    host = repository.split('/')[0]
    auth = base64.b64encode((credentials['username'] + ':' + credentials['password']).encode()).decode()
    path.write_text(json.dumps({'auths': {host: {'auth': auth}}}))
    path.chmod(0o600)
    env = {'DOCKER_CONFIG': str(auth_dir)}
    try:
        yield env
    finally:
        path.unlink(missing_ok=True)
        auth_dir.rmdir()


def registry_source(request, root, command, repository):
    image = request.get('sourceImage', '')
    if not re.fullmatch(re.escape(repository) + r'@sha256:[a-f0-9]{64}', image):
        raise ValueError('Source image must be pinned in the configured repository')
    if not re.fullmatch(r'[a-f0-9]{40}', request.get('commit', '')):
        raise ValueError('A full commit SHA is required')
    path = root / 'source-layer.tar'
    with registry_auth(request, root, repository) as env:
        command(['crane', 'export', image, str(path)], root, env)
    if path.stat().st_size > 64 * 1024 * 1024:
        raise ValueError('Source layer limit exceeded')
    archive_path = root / 'source.tgz'
    with tarfile.open(path) as archive:
        entries = archive.getmembers()
        if len(entries) != 1 or entries[0].name != 'source.tgz' or not entries[0].isfile() or entries[0].size > 48 * 1024 * 1024:
            raise ValueError('Expected one bounded source bundle')
        with archive.extractfile(entries[0]) as inp, archive_path.open('xb') as out:
            shutil.copyfileobj(inp, out)
    path.unlink()
    if hashlib.file_digest(archive_path.open('rb'), 'sha256').hexdigest() != request.get('sourceSha256'):
        raise ValueError('Source checksum mismatch')
    from source_bundle import extract
    return extract(archive_path, request, root)

"""Read a version-pinned source bundle with Resource Principal storage access."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile


def validate(request):
    bundle = request.get('sourceBundle')
    if not isinstance(bundle, dict) or set(bundle) != {'object', 'version', 'sha256'}:
        raise ValueError('sourceBundle requires object, version and sha256 only')
    if request.get('source'):
        raise ValueError('Choose one source input')
    prefix = os.environ['BUILD_SOURCE_PREFIX']
    if not prefix or not prefix.endswith('/'):
        raise ValueError('BUILD_SOURCE_PREFIX must be a non-empty directory prefix')
    name = bundle['object']
    if not isinstance(name, str) or not name.startswith(prefix) or len(name) > 1024 or any(ord(c) < 32 for c in name):
        raise ValueError('Source object is outside the configured prefix')
    version = bundle['version']
    if not isinstance(version, str) or not version.strip() or len(version) > 1024 or any(ord(c) < 32 for c in version):
        raise ValueError('An explicit object version is required')
    if not isinstance(bundle['sha256'], str) or not re.fullmatch('[a-f0-9]{64}', bundle['sha256']):
        raise ValueError('Source SHA256 required')
    return bundle


def limit(name, default):
    value = int(os.environ.get(name, str(default)))
    if value <= 0:
        raise ValueError('Source limits must be positive')
    return value


def fetch(storage, request, root):
    bundle = validate(request)
    compressed_limit = limit('BUILD_SOURCE_MAX_BYTES', 64 * 1024**2)
    unpacked_limit = limit('BUILD_SOURCE_MAX_UNPACKED_BYTES', 256 * 1024**2)
    file_limit = limit('BUILD_SOURCE_MAX_ENTRIES', 10000)
    reserve = limit('BUILD_SOURCE_RESERVE_BYTES', 16 * 1024**2)
    archive_path = root / 'source.tgz'
    response = storage.get_object(os.environ['BUILD_SOURCE_NAMESPACE'], os.environ['BUILD_SOURCE_BUCKET'],
                                  bundle['object'], version_id=bundle['version'])
    digest = hashlib.sha256()
    total = 0
    try:
        with archive_path.open('xb') as target:
            for chunk in response.data.raw.stream(1024**2, decode_content=False):
                total += len(chunk)
                if total > compressed_limit:
                    raise ValueError('Compressed source limit exceeded')
                digest.update(chunk)
                target.write(chunk)
    finally:
        response.data.raw.close()
    if digest.hexdigest() != bundle['sha256']:
        raise ValueError('Source checksum mismatch')
    source = root / 'source'
    with tarfile.open(archive_path, 'r:gz') as archive:
        entries, names, unpacked = [], set(), 0
        for member in archive:
            path = PurePosixPath(member.name)
            if (len(entries) >= file_limit or len(member.name) > 1024 or path.is_absolute()
                or '..' in path.parts or '\\' in member.name or not path.parts
                or any(ord(c) < 32 for c in member.name) or str(path) in names
                or not (member.isfile() or member.isdir()) or member.issparse()):
                raise ValueError('Unsafe source archive entry')
            names.add(str(path)); entries.append(member)
            unpacked += member.size
            if unpacked > unpacked_limit:
                raise ValueError('Unpacked source limit exceeded')
        manifest = next((m for m in entries if m.name == 'source.json' and m.isfile()), None)
        if manifest is None or manifest.size > 65536:
            raise ValueError('Source manifest missing or too large')
        info = json.loads(archive.extractfile(manifest).read())
        if info.get('schema_version') != 1 or info.get('commit') != request['commit']:
            raise ValueError('Source manifest commit mismatch')
        if shutil.disk_usage(root).free < unpacked + reserve:
            raise ValueError('Insufficient source extraction capacity')
        source.mkdir()
        for member in entries:
            target = source / member.name
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as out, archive.extractfile(member) as inp:
                    shutil.copyfileobj(inp, out)
    return source

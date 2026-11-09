"""Python build steps for an isolated Function invocation; no OCI dependency."""
import re
import json
import os
import importlib.metadata
import tomllib
import sys
from pathlib import Path


def local_packages(directory, locked_names):
    manifest = directory / 'build-local.json'
    if not manifest.exists() and not manifest.is_symlink():
        return []
    if manifest.is_symlink() or not manifest.is_file() or manifest.stat().st_size > 16384:
        raise ValueError('Invalid local package manifest')
    data = json.loads(manifest.read_text())
    if not isinstance(data, dict) or set(data) != {'schema_version', 'packages'} or type(data['schema_version']) is not int or data['schema_version'] != 1:
        raise ValueError('Invalid local package schema')
    packages = data['packages']
    if not isinstance(packages, list) or not 1 <= len(packages) <= 8:
        raise ValueError('Expected 1 to 8 local packages')
    found, paths = set(locked_names), []
    for item in packages:
        if not isinstance(item, dict) or set(item) != {'path', 'name', 'version'} or any(not isinstance(v, str) for v in item.values()):
            raise ValueError('Invalid local package entry')
        relative = item['path']
        if not relative or len(relative) > 250 or relative.startswith('/') or '\\' in relative or any(ord(c) < 32 for c in relative) or any(p in ('', '.', '..') for p in relative.split('/')):
            raise ValueError('Local package path must stay inside the project')
        path = directory / relative
        if any((directory / Path(*Path(relative).parts[:i])).is_symlink() for i in range(1, len(Path(relative).parts)+1)) or not path.is_dir() or not path.resolve().is_relative_to(directory.resolve()):
            raise ValueError('Local package links or external paths refused')
        metadata = path / 'pyproject.toml'
        if metadata.is_symlink() or not metadata.is_file() or metadata.stat().st_size > 131072:
            raise ValueError('Local package metadata required')
        project = tomllib.loads(metadata.read_text()).get('project', {})
        if project.get('name') != item['name'] or project.get('version') != item['version']:
            raise ValueError('Local package name/version mismatch')
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', item['name']) or not re.fullmatch(r'[A-Za-z0-9_.+!-]+', item['version']):
            raise ValueError('Invalid local package pin')
        name = re.sub(r'[-_.]+', '-', item['name']).lower()
        if name in found or path.resolve() in paths:
            raise ValueError('Duplicate local or locked package')
        found.add(name)
        paths.append(path.resolve())
    return paths


def build(directory, root, goal, command):
    if goal not in ('test', 'package', 'verify'):
        raise ValueError('Unsupported Python goal')
    lock = directory / 'requirements-build.lock'
    if lock.is_symlink() or not lock.is_file() or lock.stat().st_size > 128 * 1024:
        raise ValueError('A bounded requirements-build.lock is required')
    pins = lock.read_text().splitlines()
    if not pins or any(not re.fullmatch(r'[A-Za-z0-9_.-]+==[A-Za-z0-9_.+!-]+', line) for line in pins):
        raise ValueError('Build requirements must contain exact pins only')
    names = [re.sub(r'[-_.]+', '-', line.split('==')[0]).lower() for line in pins]
    if len(names) != len(set(names)) or 'pytest' not in names:
        raise ValueError('Unique pinned requirements including pytest required')
    packages = local_packages(directory, names)
    preinstalled = os.environ.get('BUILD_PREINSTALLED_LOCK', '')
    if preinstalled:
        image_lock = Path(preinstalled)
        if not image_lock.is_absolute() or image_lock.is_symlink() or not image_lock.is_file() or image_lock.stat().st_size > 128 * 1024:
            raise ValueError('Invalid image dependency lock')
        if sorted(image_lock.read_text().splitlines()) != sorted(pins):
            raise ValueError('Image dependency lock does not match source')
        for pin in pins:
            name, version = pin.split('==')
            if importlib.metadata.version(name) != version:
                raise ValueError('Installed image dependency version mismatch: ' + name)
    python = str(root / 'venv/bin/python')
    command([sys.executable, '-m', 'venv', *(['--system-site-packages'] if preinstalled else []), str(root / 'venv')], directory)
    if not preinstalled:
        command([python, '-m', 'pip', 'install', '--no-cache-dir', '--no-deps', '-r', str(lock)], directory)
    for package in packages:
        command([python, '-m', 'pip', 'install', '--no-deps', '--no-build-isolation', str(package)], directory)
    command([python, '-m', 'pip', 'install', '--no-deps', '--no-build-isolation', '.'], directory)
    command([python, '-m', 'pip', 'check'], directory)
    command([python, '-m', 'pytest', '-q', '--junitxml=python-test-reports/results.xml'], directory)
    if goal != 'test':
        command([python, '-m', 'pip', 'wheel', '--no-deps', '--no-build-isolation', '--wheel-dir', 'dist', *map(str, packages), '.'], directory)

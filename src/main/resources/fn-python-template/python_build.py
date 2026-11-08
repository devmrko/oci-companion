"""Python build steps for an isolated Function invocation; no OCI dependency."""
import re
import sys
from pathlib import Path


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
    python = str(root / 'venv/bin/python')
    command([sys.executable, '-m', 'venv', str(root / 'venv')], directory)
    command([python, '-m', 'pip', 'install', '--no-cache-dir', '--no-deps', '-r', str(lock)], directory)
    command([python, '-m', 'pip', 'install', '--no-deps', '--no-build-isolation', '.'], directory)
    command([python, '-m', 'pip', 'check'], directory)
    command([python, '-m', 'pytest', '-q', '--junitxml=python-test-reports/results.xml'], directory)
    if goal != 'test':
        command([python, '-m', 'pip', 'wheel', '--no-deps', '--no-build-isolation', '--wheel-dir', 'dist', '.'], directory)

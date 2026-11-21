#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
command -v foundryctl >/dev/null
# Install Foundry v0.5.0 from the official release first.
# Generate and inspect files before starting containers.
foundryctl gauge --no-ledger --no-updater -f casting.yaml
foundryctl forge --no-ledger --no-updater -f casting.yaml
printf '%s\n' 'Inspect pours/deployment/compose.yaml, then run:'
printf '%s\n' 'docker compose -f pours/deployment/compose.yaml up -d'

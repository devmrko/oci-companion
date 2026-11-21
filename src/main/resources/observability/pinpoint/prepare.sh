#!/bin/sh
set -eu
umask 077
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
command -v helm >/dev/null
case "$(helm version --short)" in v3.*) ;; *) echo "Helm 3 is required for this executable post-renderer." >&2; exit 1;; esac
command -v kubectl >/dev/null
chmod +x registry-renderer.sh
# Official evaluation profile pinned to a reviewed revision.
curl --fail --location --proto '=https' --tlsv1.2 \
  https://raw.githubusercontent.com/pinpoint-apm/pinpoint-kubernetes/dd96ef88be51ea414ea6c349afc3da1d5f20feb6/values-minimal.yaml \
  -o values-minimal.yaml
helm pull pinpoint --repo https://pinpoint-apm.github.io/pinpoint-kubernetes --version 4.0.1
helm template pinpoint ./pinpoint-4.0.1.tgz --namespace pinpoint \
  -f values-minimal.yaml -f values-placement.yaml --post-renderer ./registry-renderer.sh > rendered.yaml
printf '%s\n' 'Check x86 nodes, available memory (requests ~8.3GiB), storage class and rendered.yaml.'
printf '%s\n' 'kubectl get nodes -L kubernetes.io/arch'
printf '%s\n' 'kubectl get storageclass'
printf '%s\n' 'helm upgrade --install pinpoint ./pinpoint-4.0.1.tgz --namespace pinpoint --create-namespace -f values-minimal.yaml -f values-placement.yaml --post-renderer ./registry-renderer.sh --wait --wait-for-jobs --timeout 20m'
printf '%s\n' 'kubectl port-forward -n pinpoint service/pinpoint-web 18081:8080'

#!/bin/sh
# Native Kustomize image rewrites for CRI-O short-name enforcement.
set -eu
umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT HUP INT TERM
cat > "$work/resources.yaml"
cat > "$work/kustomization.yaml" <<'YAML'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources: [resources.yaml]
images:
  - name: busybox
    newName: docker.io/library/busybox
  - name: python
    newName: docker.io/library/python
  - name: bitnamilegacy/mysql
    newName: docker.io/bitnamilegacy/mysql
  - name: apachepinot/pinot
    newName: docker.io/apachepinot/pinot
  - name: pinpointdocker/pinpoint-web
    newName: docker.io/pinpointdocker/pinpoint-web
  - name: pinpointdocker/pinpoint-collector
    newName: docker.io/pinpointdocker/pinpoint-collector
  - name: pinpointdocker/pinpoint-agent
    newName: docker.io/pinpointdocker/pinpoint-agent
  - name: pinpointdocker/pinpoint-quickstart
    newName: docker.io/pinpointdocker/pinpoint-quickstart
  - name: pinpointdocker/pinpoint-hbase
    newName: docker.io/pinpointdocker/pinpoint-hbase
YAML
kubectl kustomize "$work"

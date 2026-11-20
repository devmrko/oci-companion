#!/usr/bin/env bash
# Source this file from a build step: . ./publish-build-image.sh
# OCI build_spec.yaml: env.exportedVariables: [BUILD_IMAGE_REFERENCE]
# Required: LOCAL_IMAGE, IMAGE_REPOSITORY (host/namespace/repo), IMAGE_TAG,
# REGISTRY_AUTH_FILE (existing containers-auth.json, prepared from Vault).
# Needs docker/podman-compatible docker CLI, skopeo. Never pass credentials in argv.
set -euo pipefail
set +x
: "${LOCAL_IMAGE:?}" "${IMAGE_REPOSITORY:?}" "${IMAGE_TAG:?}" "${REGISTRY_AUTH_FILE:?}"
[[ "$IMAGE_REPOSITORY" =~ ^[a-z0-9.-]+/[a-z0-9]+/[a-z0-9]+([._/-][a-z0-9]+)*$ ]]
[[ "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]
[[ -r "$REGISTRY_AUTH_FILE" ]]
command -v docker >/dev/null
command -v skopeo >/dev/null
unset BUILD_IMAGE_REFERENCE
publish_build_image() (
  set -euo pipefail
  umask 077
  image_work=$(mktemp -d)
  trap 'rm -rf -- "$image_work"' EXIT
  docker save --output "$image_work/image.tar" "$LOCAL_IMAGE" >&2
  skopeo copy --dest-authfile "$REGISTRY_AUTH_FILE" --digestfile "$image_work/digest" \
    "docker-archive:$image_work/image.tar" "docker://$IMAGE_REPOSITORY:$IMAGE_TAG" >&2
  image_digest=$(cat "$image_work/digest")
  [[ "$image_digest" =~ ^sha256:[a-f0-9]{64}$ ]]
  printf '%s@%s' "$IMAGE_REPOSITORY" "$image_digest"
)
BUILD_IMAGE_REFERENCE=$(publish_build_image)
export BUILD_IMAGE_REFERENCE
unset -f publish_build_image

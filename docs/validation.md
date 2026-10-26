# Validation — 2026-10-21

`./scripts/build.sh`: passed, Java 21, pinned Foundation source built locally,
application packaged as an executable Spring Boot JAR.

10 integration tests passed using actual temporary SQLite databases and an explicit demo
provider. Covered anonymous access, CSRF, host restriction, logout invalidation, invalid
profile rejection, page rendering, project update/persistence, scope isolation, unsafe
repository URLs, duplicate request tokens, concurrent submissions, uncertain provider
outcomes without retry, stale pipeline selection and unsupported deployment stages.

Playwright Chromium desktop (1440px) and mobile (390px): profile connection, project form,
build submission/status refresh, VM demo submission, pipeline list and all six help
sections passed. No JavaScript errors or mobile document overflow. Visual inspection of
login and project screenshots completed. Browser required macOS sandbox escalation for
its Mach port; no cloud resources were used.

Reproduce browser checks with the demo server already running on port 8091:

```sh
npm install --prefix .tools/browser --no-audit --no-fund playwright@1.49.1
# Install Chromium with .tools/browser/node_modules/.bin/playwright install chromium if needed.
node scripts/browser-smoke.cjs
```

The smoke script requires the DEMO profile and demo banner before writing sample data.
It adds one demo project per run; screenshots are kept under ignored `.tools/`.

OCI SDK adapters compile against 3.94.1. **No live OCI authentication, IAM, build or VM
deployment test has been performed.** Demo success is not evidence of cloud execution.
Generated build specs are starter templates, not verified builds of arbitrary repositories.
Functions and OKE adapters, resource provisioning, live log rendering, artifact promotion
and rollback are outside this first milestone.

Official references checked for help/specification wording:

- https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm
- https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm
- https://docs.oracle.com/en-us/iaas/Content/devops/using/deploy_instancegroups.htm
- https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/devops/deployment/create-pipeline-deployment.html

The remaining real-environment acceptance test is to connect an API-key profile with
the necessary permissions, map pre-existing build and VM pipelines, verify their source,
artifact and target settings, then execute a build and deployment and compare remote
status with the OCI console. Private Git authentication and VM agent/network readiness
must be prepared in OCI first.

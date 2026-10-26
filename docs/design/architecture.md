# OCI Companion — initial design

2026-10-21. Local single-operator web application. Delivery order: VM, Functions, OKE.

## Decisions

Use Java 21 and Spring Foundation (reviewed against Python Foundation). Reuse its MVC,
Thymeleaf, error contract and request IDs. Use application-owned security: selecting an
API-key profile from the server's OCI config authenticates a local session through an
OCI request. This is NOT a multi-user login service. Bind to loopback, retain CSRF,
rotate the session on connect, and never expose/store private keys or config contents.

SQLite is the default application metadata store; no DB server is required. Repository
interfaces expose domain records, not JDBC/JPA types. A future DB adapter must pass the
same scope/isolation and concurrency tests. Object Storage is for future exports/backups,
not a mounted live SQLite database. OCI owns pipelines, builds, deployments and artifacts;
the app owns workspace mappings and request tracking. Never treat cached state as live.

## Ports

- CloudConnection: enumerate profiles, verify a connection; immutable session scope.
- PipelineGateway: list existing pipelines, start a build/VM deployment, read run status.
- WorkspaceRepository: persist project mappings, scoped by tenancy/user/region.
- RunRepository: persist submissions before external calls, detect duplicate tokens.
- BuildRecipe: produce editable build specifications for Maven, Gradle, Python, Node.
- DeploymentTarget: target-specific validation and help (VM first).
- GuideCatalog: contextual console instructions, CLI examples, IAM notes, official URLs.

Controllers call services; services orchestrate ports; adapters own SDK/JDBC details.
No SDK models cross port boundaries. Adapters are selected with Spring configuration.
Demo adapters are available only under the explicit demo profile, with a distinct scope
and visible banner. They must never be described as verified cloud execution.

## First vertical slice and completion criteria

Profile connection → scoped project CRUD → editable repository/build settings → generated
build spec → existing DevOps pipeline selection → build or VM deployment submission →
durable request history and on-demand remote status refresh. Every workflow has OCI help.
Initial source/build settings are local planning inputs; generated specs must be committed
and pipelines configured in OCI. Starting an existing pipeline uses its OCI configuration,
not a silent override from the local form. UI must state this before running.

Initial VM execution accepts only pipelines containing instance-group deployment stages
(and optional approval/wait stages). Unsupported stages are rejected before execution.
Functions/OKE are explicitly unavailable in this milestone. Cloud resource provisioning,
private Git credentials, Git detection, live log streaming, artifact promotion and automated
rollback are future milestones, not simulated as finished functionality.

## Failure and persistence semantics

Save SUBMITTING with a unique request token and immutable pipeline ID before calling OCI.
Repeated tokens return the existing record; a token cannot be reused with another request.
No automatic resubmission after timeout/crash. Mark unconfirmed outcomes UNKNOWN and
direct the operator to OCI; a restart also renders stale SUBMITTING as unknown. Refresh
requires a known remote run ID. OCI can complete even if saving the response fails.
Short DB transactions must not encompass remote network calls. Profile/tenancy/user/region
scope is rechecked when credentials are loaded, preventing config edits from switching actor.

## Verification

Real SQLite integration tests: scope isolation, update behavior, duplicate submissions.
Security tests: anonymous access, CSRF, session connection/logout, demo identity isolation.
Service tests: unsupported targets, provider failure and retries. SDK compilation plus mock
adapter tests do not certify actual OCI deployment. No cloud resources are created by tests.

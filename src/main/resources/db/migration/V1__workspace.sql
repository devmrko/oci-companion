CREATE TABLE workspaces (
  id TEXT PRIMARY KEY, scope TEXT NOT NULL, name TEXT NOT NULL,
  repository_url TEXT NOT NULL, branch TEXT NOT NULL, recipe TEXT NOT NULL,
  compartment_id TEXT NOT NULL, build_pipeline_id TEXT NOT NULL,
  deploy_pipeline_id TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE INDEX workspace_scope ON workspaces(scope);
CREATE TABLE runs (
  id TEXT PRIMARY KEY, scope TEXT NOT NULL, workspace_id TEXT NOT NULL,
  kind TEXT NOT NULL, pipeline_id TEXT NOT NULL, remote_id TEXT,
  status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  FOREIGN KEY(workspace_id) REFERENCES workspaces(id)
);
CREATE INDEX run_scope ON runs(scope, workspace_id, created_at);

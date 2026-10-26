package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Workspace;
import io.github.devmrko.companion.port.WorkspaceRepository;
import io.github.devmrko.companion.service.Errors;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkspaceRepository implements WorkspaceRepository {
    private final JdbcTemplate db;
    private static final RowMapper<Workspace> MAPPER = (r, i) -> new Workspace(r.getString("id"),
        r.getString("name"), r.getString("repository_url"), r.getString("branch"), r.getString("recipe"),
        r.getString("compartment_id"), r.getString("build_pipeline_id"), r.getString("deploy_pipeline_id"),
        Instant.parse(r.getString("created_at")));
    public JdbcWorkspaceRepository(JdbcTemplate db) { this.db = db; }
    public List<Workspace> list(String scope) {
        return db.query("SELECT * FROM workspaces WHERE scope=? ORDER BY created_at DESC", MAPPER, scope);
    }
    public Optional<Workspace> find(String scope, String id) {
        return db.query("SELECT * FROM workspaces WHERE scope=? AND id=?", MAPPER, scope, id).stream().findFirst();
    }
    public void save(String scope, Workspace w) {
        if (find(scope, w.id()).isPresent()) {
            int changed = db.update("UPDATE workspaces SET name=?,repository_url=?,branch=?,recipe=?,compartment_id=?,build_pipeline_id=?,deploy_pipeline_id=? WHERE id=? AND scope=?",
                w.name(),w.repositoryUrl(),w.branch(),w.recipe(),w.compartmentId(),w.buildPipelineId(),w.deployPipelineId(),w.id(),scope);
            if (changed != 1) throw Errors.CONFLICT.exception();
        } else {
            db.update("INSERT INTO workspaces VALUES (?,?,?,?,?,?,?,?,?,?)",w.id(),scope,w.name(),w.repositoryUrl(),w.branch(),w.recipe(),w.compartmentId(),w.buildPipelineId(),w.deployPipelineId(),w.createdAt().toString());
        }
    }
}

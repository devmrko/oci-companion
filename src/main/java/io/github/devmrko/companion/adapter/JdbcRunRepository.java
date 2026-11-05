package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Run;
import io.github.devmrko.companion.port.RunRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
@org.springframework.context.annotation.Profile("legacy")
public class JdbcRunRepository implements RunRepository {
    private final JdbcTemplate db;
    private static final RowMapper<Run> MAPPER = (r, i) -> new Run(r.getString("id"),r.getString("workspace_id"),
        r.getString("kind"),r.getString("pipeline_id"),r.getString("remote_id"),r.getString("status"),
        Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")));
    public JdbcRunRepository(JdbcTemplate db) { this.db=db; }
    public List<Run> list(String scope, String workspaceId) {
        return db.query("SELECT * FROM runs WHERE scope=? AND workspace_id=? ORDER BY created_at DESC", MAPPER, scope, workspaceId);
    }
    public Optional<Run> find(String scope, String id) {
        return db.query("SELECT * FROM runs WHERE scope=? AND id=?", MAPPER, scope, id).stream().findFirst();
    }
    public boolean reserve(String scope, Run r) {
        return db.update("INSERT INTO runs VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
            r.id(),scope,r.workspaceId(),r.kind(),r.pipelineId(),r.remoteId(),r.status(),r.createdAt().toString(),r.updatedAt().toString()) == 1;
    }
    public void update(String scope, String id, String remoteId, String status) {
        db.update("UPDATE runs SET remote_id=?,status=?,updated_at=? WHERE scope=? AND id=?", remoteId,status,Instant.now().toString(),scope,id);
    }
}

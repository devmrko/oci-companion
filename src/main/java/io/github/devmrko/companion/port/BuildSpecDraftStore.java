package io.github.devmrko.companion.port;

import java.util.Optional;

/** Local editing drafts, never an authority for remote execution. */
public interface BuildSpecDraftStore {
    record Draft(String revision,String content,String savedAt) {}
    Optional<Draft> read(String identity);
    void save(String identity,Draft draft);
    void delete(String identity);
}

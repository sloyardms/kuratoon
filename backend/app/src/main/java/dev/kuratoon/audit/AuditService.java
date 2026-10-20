package dev.kuratoon.audit;

import dev.kuratoon.auth.CurrentUser;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code audit_log} for every admin mutation (docs/spec/04-auth-users.md), inside the
 * caller's transaction so the entry exists only if the change commits. The actor is the
 * authenticated user, or null for system actions (e.g. the env admin bootstrap).
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param action e.g. {@code MEDIA_TYPE_CREATED}
     * @param details small JSON-serializable map; never passwords, tokens or cookies
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, Object targetId, Map<String, ?> details) {
        jdbc.sql("""
                insert into audit_log (actor_user_id, action, target_type, target_id, details)
                values (:actor, :action, :targetType, :targetId, cast(:details as jsonb))
                """)
                .param("actor", CurrentUser.id().orElse(null))
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId == null ? null : targetId.toString())
                .param("details", details == null ? null : json.writeValueAsString(details))
                .update();
    }
}

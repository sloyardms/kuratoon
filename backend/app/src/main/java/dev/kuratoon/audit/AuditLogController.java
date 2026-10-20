package dev.kuratoon.audit;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import jakarta.validation.Valid;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Audit log page API: newest first, optional filters, offset pagination (docs/spec/04-auth-users.md). */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
class AuditLogController {

    private static final String FILTER = """
            from audit_log a left join app_user u on u.id = a.actor_user_id
            where (cast(:action as text) is null or a.action = :action)
              and (cast(:targetType as text) is null or a.target_type = :targetType)
              and (cast(:actorId as uuid) is null or a.actor_user_id = :actorId)
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLogController(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record Actor(UUID id, String username) {}

    /** @param actor null for system actions (or a deleted user) */
    record AuditEntry(
            long id, Instant createdAt, Actor actor, String action, String targetType, String targetId, JsonNode details) {}

    @GetMapping
    @Transactional(readOnly = true)
    public OffsetPage<AuditEntry> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID actorId,
            @Valid PageParams paging) {
        PageRequest page = paging.toPageRequest(Sort.unsorted());
        long total = jdbc.sql("select count(*) " + FILTER)
                .param("action", blankToNull(action))
                .param("targetType", blankToNull(targetType))
                .param("actorId", actorId)
                .query(Long.class)
                .single();
        List<AuditEntry> items = jdbc.sql("""
                        select a.id, a.created_at, a.actor_user_id, u.username, a.action, a.target_type,
                               a.target_id, a.details::text as details
                        """ + FILTER + """
                        order by a.created_at desc, a.id desc
                        limit :limit offset :offset
                        """)
                .param("action", blankToNull(action))
                .param("targetType", blankToNull(targetType))
                .param("actorId", actorId)
                .param("limit", page.getPageSize())
                .param("offset", page.getOffset())
                .query(this::entry)
                .list();
        int totalPages = (int) ((total + page.getPageSize() - 1) / page.getPageSize());
        return new OffsetPage<>(items, page.getPageNumber(), page.getPageSize(), total, totalPages);
    }

    private AuditEntry entry(ResultSet rs, int row) throws SQLException {
        UUID actorId = rs.getObject("actor_user_id", UUID.class);
        String details = rs.getString("details");
        return new AuditEntry(
                rs.getLong("id"),
                rs.getTimestamp("created_at").toInstant(),
                actorId == null ? null : new Actor(actorId, rs.getString("username")),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                details == null ? null : json.readTree(details));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

package dev.kuratoon.support;

import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Resets the database between tests: truncates every table except the Flyway history and
 * {@code media_type}, then deletes media types that are not one of the seeded ones.
 */
@TestComponent
public class DatabaseCleaner {

    /** Ids from V6__seed_media_types.sql. */
    public static final List<String> SEED_MEDIA_TYPE_IDS = List.of(
            "01a15850-2d3f-7cd4-9f7e-58433cd2334d",
            "01a15850-2d40-762e-9cde-cbc2119a3323",
            "01a15850-2d41-761b-93b0-218c24b90e10",
            "01a15850-2d42-70f1-aa17-9ec5cf2535a1",
            "01a15850-2d43-7223-84b7-451f91eeca18",
            "01a15850-2d44-7216-a287-0d9d0559d4a3",
            "01a15850-2d45-7bcc-a483-94da2f2b829a");

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        List<String> tables = jdbc.queryForList(
                """
                select quote_ident(tablename) from pg_tables
                where schemaname = 'public' and tablename not in ('flyway_schema_history', 'media_type')
                """,
                String.class);
        jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
        jdbc.update(
                "delete from media_type where not (id::text = any (?))",
                (Object) SEED_MEDIA_TYPE_IDS.toArray(String[]::new));
    }
}

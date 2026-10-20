package dev.kuratoon.settings;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Typed access to {@code app_setting} (docs/spec/01-data-model.md). A missing row means the default.
 */
@Service
public class AppSettings {

    public static final String REGISTRATION_ENABLED = "registration.enabled";
    public static final String MOMENT_SUBMISSIONS_ENABLED = "moments.submissions.enabled";
    public static final String SETUP_COMPLETED = "setup.completed";

    private final JdbcClient jdbc;
    private final Clock clock;

    public AppSettings(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return jdbc.sql("select (value #>> '{}')::boolean from app_setting where key = :key and jsonb_typeof(value) = 'boolean'")
                .param("key", key)
                .query(Boolean.class)
                .optional()
                .orElse(defaultValue);
    }

    @Transactional
    public void setBoolean(String key, boolean value) {
        jdbc.sql("""
                        insert into app_setting (key, value, updated_at) values (:key, to_jsonb(:value), :now)
                        on conflict (key) do update set value = excluded.value, updated_at = excluded.updated_at
                        """)
                .param("key", key)
                .param("value", value)
                .param("now", Timestamp.from(clock.instant()))
                .update();
    }

    public boolean registrationEnabled() {
        return getBoolean(REGISTRATION_ENABLED, false);
    }

    public boolean momentSubmissionsEnabled() {
        return getBoolean(MOMENT_SUBMISSIONS_ENABLED, false);
    }
}

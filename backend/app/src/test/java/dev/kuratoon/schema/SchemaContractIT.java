package dev.kuratoon.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kuratoon.TestcontainersConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The migrated schema must match the column contract in docs/spec/01-data-model.md. Types are
 * Postgres udt names; a trailing {@code ?} marks a nullable column.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SchemaContractIT {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        // Users and settings
        table("app_user", "id uuid, username citext, email citext?, password_hash text, role text, enabled bool,"
                + " token_version int4, created_at timestamptz, updated_at timestamptz, last_login_at timestamptz?");
        table("refresh_token", "id uuid, user_id uuid, family_id uuid, token_hash text, expires_at timestamptz,"
                + " rotated_at timestamptz?, revoked_at timestamptz?, created_at timestamptz");
        table("password_reset_token", "id uuid, user_id uuid, token_hash text, expires_at timestamptz,"
                + " used_at timestamptz?, created_at timestamptz");
        table("app_setting", "key text, value jsonb, updated_at timestamptz");
        table("audit_log", "id int8, actor_user_id uuid?, action text, target_type text, target_id text?,"
                + " details jsonb?, created_at timestamptz");
        table("user_preference", "user_id uuid, theme text, adult_mode text, reader_max_width int4,"
                + " updated_at timestamptz");
        table("user_hidden_media_type", "user_id uuid, media_type_id uuid");
        table("user_excluded_tag", "user_id uuid, tag_id uuid");
        // Library
        table("media_type", "id uuid, name text, slug text, color text, sort_order int4, created_at timestamptz");
        table("media", "id uuid, title text, description text?, media_type_id uuid, language text,"
                + " publication_status text, publication_year int2?, is_adult bool, source_url text?,"
                + " plugin_id text?, locked_fields _text, cover_file text?, cover_crop jsonb?, cover_version int4,"
                + " chapter_count int4, latest_chapter_added_at timestamptz?, avg_rating numeric?,"
                + " rating_count int4, favorite_count int4, metadata_refreshed_at timestamptz?,"
                + " created_at timestamptz, updated_at timestamptz");
        table("media_title", "id uuid, media_id uuid, title text, language text?");
        table("creator", "id uuid, name text, normalized_name text, created_at timestamptz");
        table("media_creator", "media_id uuid, creator_id uuid, role text");
        table("tag", "id uuid, name text, slug text, kind text, created_at timestamptz");
        table("media_tag", "media_id uuid, tag_id uuid");
        table("parody", "id uuid, name text, normalized_name text, created_at timestamptz");
        table("media_parody", "media_id uuid, parody_id uuid");
        table("character", "id uuid, name text, normalized_name text, parody_id uuid?, created_at timestamptz");
        table("media_character", "media_id uuid, character_id uuid");
        table("media_relation", "id uuid, from_media_id uuid, to_media_id uuid, type text, created_at timestamptz");
        table("chapter", "id uuid, media_id uuid, number numeric, title text?, folder text, pages jsonb,"
                + " page_count int4, created_at timestamptz, updated_at timestamptz");
        // Per-user reading data
        table("chapter_progress", "user_id uuid, chapter_id uuid, media_id uuid, page_index int4,"
                + " page_offset float4, completed_at timestamptz?, last_read_at timestamptz?, updated_at timestamptz");
        table("user_media_state", "user_id uuid, media_id uuid, intent text?, chapters_read int4,"
                + " last_read_chapter_id uuid?, last_read_at timestamptz?, updated_at timestamptz");
        table("read_event", "id int8, user_id uuid, media_id uuid, chapter_id uuid, read_at timestamptz");
        table("favorite_media", "user_id uuid, media_id uuid, created_at timestamptz");
        table("favorite_chapter", "user_id uuid, chapter_id uuid, created_at timestamptz");
        table("rating", "user_id uuid, media_id uuid, score int2, created_at timestamptz, updated_at timestamptz");
        table("collection", "id uuid, user_id uuid, name text, description text?, created_at timestamptz,"
                + " updated_at timestamptz");
        table("collection_media", "collection_id uuid, media_id uuid, added_at timestamptz");
        // Moments
        table("moment", "id uuid, chapter_id uuid, page_index int4, crop jsonb, title text, description text?,"
                + " status text, created_by uuid?, reviewed_by uuid?, reviewed_at timestamptz?,"
                + " created_at timestamptz, updated_at timestamptz");
        // Ingestion and fetching
        table("import_job", "id uuid, source_folder text, kind text?, status text, media_id uuid?, plugin_id text?,"
                + " message text?, detail jsonb?, attempts int4, created_at timestamptz, updated_at timestamptz,"
                + " finished_at timestamptz?");
        table("domain_session", "domain text, cookies jsonb, user_agent text, expires_at timestamptz,"
                + " updated_at timestamptz");
    }

    private static void table(String name, String columns) {
        EXPECTED.put(name, columns);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    void everySpecTableHasExactlyTheSpecColumns() {
        Map<String, String> actual = new TreeMap<>();
        jdbc.query(
                """
                select table_name, string_agg(column_name || ' ' || udt_name
                        || case when is_nullable = 'YES' then '?' else '' end, ', ' order by ordinal_position)
                from information_schema.columns
                where table_schema = 'public' and table_name <> 'flyway_schema_history'
                group by table_name
                """,
                rs -> {
                    actual.put(rs.getString(1), rs.getString(2));
                });

        assertThat(actual).isEqualTo(new TreeMap<>(EXPECTED));
    }

    @Test
    void specIndexesExist() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);
        assertThat(indexes)
                .contains(
                        "media_title_trgm_idx",
                        "media_title_title_trgm_idx",
                        "creator_name_trgm_idx",
                        "media_media_type_id_idx",
                        "media_created_at_idx",
                        "media_latest_chapter_added_at_idx",
                        "media_publication_status_idx",
                        "media_language_idx",
                        "media_tag_tag_id_media_id_idx",
                        "chapter_media_id_number_idx",
                        "chapter_created_at_idx",
                        "chapter_progress_user_id_last_read_at_idx",
                        "chapter_progress_user_id_media_id_idx",
                        "user_media_state_user_id_last_read_at_idx",
                        "read_event_read_at_idx",
                        "read_event_media_id_read_at_idx",
                        "moment_chapter_id_status_idx",
                        "moment_status_created_at_idx",
                        "import_job_status_created_at_idx",
                        "import_job_active_source_folder_key",
                        "audit_log_created_at_idx",
                        "character_normalized_name_parody_id_key");

        String trgm = jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'media_title_trgm_idx'", String.class);
        assertThat(trgm).contains("USING gin").contains("gin_trgm_ops");
        String activeJob = jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'import_job_active_source_folder_key'",
                String.class);
        assertThat(activeJob).contains("UNIQUE").contains("WHERE (finished_at IS NULL)");
    }

    @Test
    void mediaTypesAreSeeded() {
        assertThat(jdbc.queryForList("select slug from media_type order by sort_order", String.class))
                .containsExactly("manga", "manhwa", "manhua", "comic", "webtoon", "doujinshi", "one-shot");
    }

    @Test
    void characterUniquenessTreatsNullParodyAsAValue() {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("insert into character (id, name, normalized_name) values (?, 'Rin', 'rin')", UUID.randomUUID());
            assertThatThrownBy(() -> jdbc.update(
                            "insert into character (id, name, normalized_name) values (?, 'RIN', 'rin')",
                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    @Test
    void chapterPagesMustBeAnArrayMatchingPageCount() {
        insertChapter("[{\"f\":\"001.jpg\",\"w\":1,\"h\":1}]", 1);
        assertThatThrownBy(() -> insertChapter("[]", 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChapter("{\"f\":1}", 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneActiveImportJobPerFolder() {
        String finished = "insert into import_job (id, source_folder, status, finished_at) values (?, 'a', 'DONE', now())";
        String active = "insert into import_job (id, source_folder, status) values (?, 'a', 'QUEUED')";
        inRolledBackTransaction(finished, active);
        assertThatThrownBy(() -> inRolledBackTransaction(finished, active, active))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Inserts a media and one chapter in a transaction that is always rolled back. */
    private void insertChapter(String pagesJson, int pageCount) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            UUID mediaId = UUID.randomUUID();
            jdbc.update(
                    "insert into media (id, title, media_type_id, language)"
                            + " select ?, 'T', id, 'en' from media_type where slug = 'manga'",
                    mediaId);
            jdbc.update(
                    "insert into chapter (id, media_id, number, folder, pages, page_count)"
                            + " values (?, ?, 1, 'c1', ?::jsonb, ?)",
                    UUID.randomUUID(), mediaId, pagesJson, pageCount);
        });
    }

    /** Runs each statement (with a fresh UUID as its only argument) in one rolled-back transaction. */
    private void inRolledBackTransaction(String... statements) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            for (String sql : statements) {
                jdbc.update(sql, UUID.randomUUID());
            }
        });
    }
}

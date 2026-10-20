package dev.kuratoon.tag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagAdminIT extends ApiTestBase {

    private static final String BASE = "/api/v1/admin/tags";

    @Test
    void createListFilterByKindAndAudit() throws Exception {
        create(BASE, """
                {"name": "Action", "kind": "GENRE"}
                """);
        create(BASE, """
                {"name": "Rescan needed", "kind": "LIBRARY"}
                """);
        create(BASE, """
                {"name": "HQ", "kind": "LIBRARY"}
                """);

        assertThat(get(BASE + "?kind=LIBRARY")).hasStatusOk().bodyJson().extractingPath("$.items[*].slug")
                .asArray().containsExactly("hq", "rescan-needed");
        assertThat(get(BASE + "?q=act")).bodyJson().extractingPath("$.items[*].name").asArray()
                .containsExactly("Action");
        assertThat(auditCount("TAG_CREATED")).isEqualTo(3);
    }

    @Test
    void sameSlugIsAllowedInTheOtherKindButNotInTheSameKind() throws Exception {
        create(BASE, """
                {"name": "Romance", "kind": "GENRE"}
                """);
        create(BASE, """
                {"name": "Romance", "kind": "LIBRARY"}
                """);

        assertThat(post(BASE, """
                {"name": "ROMANCE!", "kind": "GENRE"}
                """)).hasStatus(409);
        assertThat(post(BASE, """
                {"name": "Love", "slug": "romance", "kind": "GENRE"}
                """)).hasStatus(409);
    }

    @Test
    void nonLatinNameRequiresExplicitSlug() throws Exception {
        assertThat(post(BASE, """
                {"name": "恋愛", "kind": "GENRE"}
                """)).hasStatus(400).bodyJson().extractingPath("$.detail").asString().contains("slug is required");
        create(BASE, """
                {"name": "恋愛", "slug": "renai", "kind": "GENRE"}
                """);
    }

    @Test
    void rejectsMissingKindAndBadSlug() {
        assertThat(post(BASE, """
                {"name": "Action", "slug": "-bad-"}
                """)).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                .contains("kind", "slug");
        assertThat(post(BASE, """
                {"name": "Action", "kind": "MOOD"}
                """)).hasStatus(400);
    }

    @Test
    void updateCanMoveTagToOtherKindUnlessSlugTaken() throws Exception {
        UUID genre = create(BASE, """
                {"name": "Drama", "kind": "GENRE"}
                """);
        create(BASE, """
                {"name": "Drama", "kind": "LIBRARY"}
                """);

        assertThat(put(BASE + "/" + genre, """
                {"name": "Drama", "kind": "LIBRARY"}
                """)).hasStatus(409);
        assertThat(put(BASE + "/" + genre, """
                {"name": "Melodrama", "kind": "GENRE"}
                """)).hasStatusOk().bodyJson().extractingPath("$.slug").isEqualTo("melodrama");
    }

    @Test
    void deletingTagInUseRemovesItFromMedia() throws Exception {
        Tag tag = data.aTag("Comedy", "comedy", TagKind.GENRE);
        data.aMedia("Gintama").tag(tag).persist();

        assertThat(delete(BASE + "/{id}", tag.getId())).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from media_tag", Integer.class)).isZero();
        assertThat(auditCount("TAG_DELETED")).isEqualTo(1);
    }
}

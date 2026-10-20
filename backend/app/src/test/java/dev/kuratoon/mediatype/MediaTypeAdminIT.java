package dev.kuratoon.mediatype;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaTypeAdminIT extends ApiTestBase {

    private static final String BASE = "/api/v1/admin/media-types";

    @Test
    void listsSeededTypesInSortOrder() {
        assertThat(get(BASE))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items[*].slug")
                .asArray()
                .containsExactly("manga", "manhwa", "manhua", "comic", "webtoon", "doujinshi", "one-shot");
        assertThat(get(BASE + "?page=0&size=2"))
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.items").asArray().hasSize(2);
                    assertThat(json).extractingPath("$.totalItems").isEqualTo(7);
                    assertThat(json).extractingPath("$.totalPages").isEqualTo(4);
                });
        assertThat(get(BASE + "?q=MANH")).bodyJson().extractingPath("$.items[*].slug").asArray()
                .containsExactly("manhwa", "manhua");
    }

    @Test
    void createDerivesSlugFromNameAndAudits() throws Exception {
        UUID id = create(BASE, """
                {"name": "Light Novel Art", "color": "#00aaff", "sortOrder": 10}
                """);

        assertThat(get(BASE + "/{id}", id))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.name").isEqualTo("Light Novel Art");
                    assertThat(json).extractingPath("$.slug").isEqualTo("light-novel-art");
                    assertThat(json).extractingPath("$.color").isEqualTo("#00aaff");
                    assertThat(json).extractingPath("$.sortOrder").isEqualTo(10);
                });
        assertThat(auditCount("MEDIA_TYPE_CREATED")).isEqualTo(1);
        assertThat(UUID.fromString(id.toString()).version()).isEqualTo(7);
    }

    @Test
    void nonLatinNameRequiresExplicitSlug() throws Exception {
        assertThat(post(BASE, """
                {"name": "漫画", "color": "#00aaff"}
                """))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("slug is required");

        UUID id = create(BASE, """
                {"name": "漫画", "slug": "manga-jp", "color": "#00aaff"}
                """);
        assertThat(get(BASE + "/{id}", id)).bodyJson().extractingPath("$.slug").isEqualTo("manga-jp");
    }

    @Test
    void rejectsInvalidInput() {
        assertThat(post(BASE, """
                {"name": "", "slug": "Bad Slug", "color": "red"}
                """))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("name", "slug", "color");
    }

    @Test
    void duplicateNameOrSlugIsConflict() {
        assertThat(post(BASE, """
                {"name": "Manga", "slug": "manga-2", "color": "#000000"}
                """)).hasStatus(409);
        assertThat(post(BASE, """
                {"name": "Manga Two", "slug": "manga", "color": "#000000"}
                """)).hasStatus(409);
        // Derived slug collides with the seeded "one-shot".
        assertThat(post(BASE, """
                {"name": "One Shot", "color": "#000000"}
                """)).hasStatus(409);
        assertThat(auditCount("MEDIA_TYPE_CREATED")).isZero();
    }

    @Test
    void updateChangesFieldsAndChecksDuplicates() throws Exception {
        UUID id = create(BASE, """
                {"name": "Artbook", "color": "#111111"}
                """);

        assertThat(put(BASE + "/" + id, """
                {"name": "Art Book", "slug": "art-book", "color": "#222222", "sortOrder": 3}
                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo("art-book");
        // Keeping its own name/slug is not a duplicate.
        assertThat(put(BASE + "/" + id, """
                {"name": "Art Book", "slug": "art-book", "color": "#333333"}
                """)).hasStatusOk();
        assertThat(put(BASE + "/" + id, """
                {"name": "Manhwa", "slug": "art-book", "color": "#333333"}
                """)).hasStatus(409);
        assertThat(auditCount("MEDIA_TYPE_UPDATED")).isEqualTo(2);
    }

    @Test
    void deleteUnusedTypeSucceeds() throws Exception {
        UUID id = create(BASE, """
                {"name": "Temporary", "color": "#111111"}
                """);

        assertThat(delete(BASE + "/{id}", id)).hasStatus(204);
        assertThat(get(BASE + "/{id}", id)).hasStatus(404);
        assertThat(auditCount("MEDIA_TYPE_DELETED")).isEqualTo(1);
    }

    @Test
    void deletingTypeInUseIsConflict() {
        data.aMedia("Naruto").type("manga").persist();
        UUID manga = data.mediaType("manga").getId();

        assertThat(delete(BASE + "/{id}", manga))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("in use");
        assertThat(get(BASE + "/{id}", manga)).hasStatusOk();
        assertThat(auditCount("MEDIA_TYPE_DELETED")).isZero();
    }

    @Test
    void unknownIdIsNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThat(get(BASE + "/{id}", unknown)).hasStatus(404);
        assertThat(put(BASE + "/" + unknown, """
                {"name": "X", "color": "#111111"}
                """)).hasStatus(404);
        assertThat(delete(BASE + "/{id}", unknown)).hasStatus(404);
    }
}

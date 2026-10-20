package dev.kuratoon.parody;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.character.StoryCharacter;
import dev.kuratoon.support.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParodyAndCharacterAdminIT extends ApiTestBase {

    private static final String PARODIES = "/api/v1/admin/parodies";
    private static final String CHARACTERS = "/api/v1/admin/characters";

    @Test
    void parodyCrudWithAudit() throws Exception {
        UUID id = create(PARODIES, """
                {"name": "Naruto"}
                """);

        assertThat(post(PARODIES, """
                {"name": " NARUTO "}
                """)).hasStatus(409);
        assertThat(put(PARODIES + "/" + id, """
                {"name": "Naruto Shippuden"}
                """)).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("Naruto Shippuden");
        assertThat(get(PARODIES + "?q=shipp")).bodyJson().extractingPath("$.items").asArray().hasSize(1);
        assertThat(delete(PARODIES + "/{id}", id)).hasStatus(204);

        assertThat(auditCount("PARODY_CREATED")).isEqualTo(1);
        assertThat(auditCount("PARODY_UPDATED")).isEqualTo(1);
        assertThat(auditCount("PARODY_DELETED")).isEqualTo(1);
    }

    @Test
    void characterIsUniquePerNormalizedNameAndParody() throws Exception {
        Parody naruto = data.aParody("Naruto");
        Parody fate = data.aParody("Fate");

        UUID rinNaruto = create(CHARACTERS, """
                {"name": "Rin", "parodyId": "%s"}
                """.formatted(naruto.getId()));
        create(CHARACTERS, """
                {"name": "Rin", "parodyId": "%s"}
                """.formatted(fate.getId()));
        create(CHARACTERS, """
                {"name": "Rin"}
                """);

        assertThat(post(CHARACTERS, """
                {"name": "RIN", "parodyId": "%s"}
                """.formatted(naruto.getId()))).hasStatus(409);
        // A NULL parody counts as a value: only one parody-less "rin".
        assertThat(post(CHARACTERS, """
                {"name": " rin "}
                """)).hasStatus(409);
        assertThat(put(CHARACTERS + "/" + rinNaruto, """
                {"name": "Rin", "parodyId": "%s"}
                """.formatted(fate.getId()))).hasStatus(409);

        assertThat(get(CHARACTERS + "/{id}", rinNaruto))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.parody.name").isEqualTo("Naruto");
                    assertThat(json).extractingPath("$.parody.id").isEqualTo(naruto.getId().toString());
                });
        assertThat(get(CHARACTERS + "?parodyId=" + fate.getId())).bodyJson().extractingPath("$.items").asArray()
                .hasSize(1);
        assertThat(auditCount("CHARACTER_CREATED")).isEqualTo(3);
    }

    @Test
    void unknownParodyOnCharacterIsBadRequest() {
        assertThat(post(CHARACTERS, """
                {"name": "Rin", "parodyId": "%s"}
                """.formatted(UUID.randomUUID()))).hasStatus(400);
    }

    @Test
    void deletingParodyDetachesItsCharacters() {
        Parody parody = data.aParody("Bleach");
        StoryCharacter ichigo = data.aCharacter("Ichigo", parody);
        data.aMedia("Bleach doujin").type("doujinshi").parody(parody).character(ichigo).persist();

        assertThat(delete(PARODIES + "/{id}", parody.getId())).hasStatus(204);
        assertThat(get(CHARACTERS + "/{id}", ichigo.getId())).hasStatusOk().bodyJson().extractingPath("$.parody")
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from media_parody", Integer.class)).isZero();
    }

    @Test
    void deletingParodyThatWouldDuplicateACharacterIsConflict() {
        Parody parody = data.aParody("Fate");
        data.aCharacter("Saber", parody);
        data.aCharacter("Saber", null);

        assertThat(delete(PARODIES + "/{id}", parody.getId()))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("Saber");
        assertThat(get(PARODIES + "/{id}", parody.getId())).hasStatusOk();
    }

    @Test
    void deletingCharacterRemovesItFromMedia() {
        StoryCharacter rin = data.aCharacter("Rin", null);
        data.aMedia("Some doujin").type("doujinshi").character(rin).persist();

        assertThat(delete(CHARACTERS + "/{id}", rin.getId())).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from media_character", Integer.class)).isZero();
        assertThat(auditCount("CHARACTER_DELETED")).isEqualTo(1);
    }
}

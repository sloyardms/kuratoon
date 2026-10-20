package dev.kuratoon.creator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatorAdminIT extends ApiTestBase {

    private static final String BASE = "/api/v1/admin/creators";

    @Test
    void createListUpdateDeleteWithAudit() throws Exception {
        UUID id = create(BASE, """
                {"name": "  Oda   Eiichiro "}
                """);
        create(BASE, """
                {"name": "Toriyama Akira"}
                """);

        assertThat(get(BASE + "/{id}", id)).hasStatusOk().bodyJson().extractingPath("$.name")
                .isEqualTo("Oda   Eiichiro");
        assertThat(get(BASE + "?q=oda")).bodyJson().extractingPath("$.items[*].name").asArray()
                .containsExactly("Oda   Eiichiro");
        assertThat(get(BASE)).bodyJson().extractingPath("$.items[*].name").asArray()
                .containsExactly("Oda   Eiichiro", "Toriyama Akira");

        assertThat(put(BASE + "/" + id, """
                {"name": "Eiichiro Oda"}
                """)).hasStatusOk();
        assertThat(delete(BASE + "/{id}", id)).hasStatus(204);

        assertThat(auditCount("CREATOR_CREATED")).isEqualTo(2);
        assertThat(auditCount("CREATOR_UPDATED")).isEqualTo(1);
        assertThat(auditCount("CREATOR_DELETED")).isEqualTo(1);
    }

    @Test
    void duplicateNormalizedNameIsConflict() throws Exception {
        UUID id = create(BASE, """
                {"name": "Oda Eiichiro"}
                """);
        create(BASE, """
                {"name": "Kishimoto Masashi"}
                """);

        assertThat(post(BASE, """
                {"name": "ODA  eiichiro"}
                """)).hasStatus(409);
        assertThat(put(BASE + "/" + id, """
                {"name": "kishimoto masashi"}
                """)).hasStatus(409);
        // Renaming to a different spelling of its own name is fine.
        assertThat(put(BASE + "/" + id, """
                {"name": "ODA EIICHIRO"}
                """)).hasStatusOk();
    }

    @Test
    void blankNameIsBadRequest() {
        assertThat(post(BASE, """
                {"name": "   "}
                """)).hasStatus(400);
    }

    @Test
    void deletingCreditedCreatorIsConflict() {
        Creator creator = data.aCreator("Kishimoto Masashi");
        data.aMedia("Naruto").author(creator).persist();

        assertThat(delete(BASE + "/{id}", creator.getId()))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("credited");
        assertThat(get(BASE + "/{id}", creator.getId())).hasStatusOk();
    }
}

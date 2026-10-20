package dev.kuratoon.media;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.character.StoryCharacter;
import dev.kuratoon.creator.Creator;
import dev.kuratoon.parody.Parody;
import dev.kuratoon.support.ApiTestBase;
import dev.kuratoon.tag.Tag;
import dev.kuratoon.tag.TagKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaReadIT extends ApiTestBase {

    @Test
    void mediaDetailIncludesAllHeaderData() {
        Creator kishimoto = data.aCreator("Kishimoto Masashi");
        Creator ikemoto = data.aCreator("Ikemoto Mikio");
        Tag action = data.aTag("Action", "action", TagKind.GENRE);
        Tag hq = data.aTag("HQ", "hq", TagKind.LIBRARY);
        Parody parody = data.aParody("Naruto");
        StoryCharacter rin = data.aCharacter("Rin", parody);
        Media media = data.aMedia("Naruto")
                .type("manga")
                .language("ja")
                .altTitle("ナルト")
                .author(kishimoto)
                .artist(kishimoto)
                .artist(ikemoto)
                .tag(action)
                .tag(hq)
                .parody(parody)
                .character(rin)
                .with(m -> {
                    m.setDescription("A ninja story.");
                    m.setPublicationStatus(PublicationStatus.COMPLETED);
                    m.setPublicationYear((short) 1999);
                })
                .chapters("1", "2", "2.5", "5")
                .persist();

        assertThat(get("/api/v1/media/{id}", media.getId()))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.title").isEqualTo("Naruto");
                    assertThat(json).extractingPath("$.alternativeTitles[*].title").asArray()
                            .containsExactly("ナルト");
                    assertThat(json).extractingPath("$.type.slug").isEqualTo("manga");
                    assertThat(json).extractingPath("$.type.color").isNotNull();
                    assertThat(json).extractingPath("$.language").isEqualTo("ja");
                    assertThat(json).extractingPath("$.publicationStatus").isEqualTo("COMPLETED");
                    assertThat(json).extractingPath("$.publicationYear").isEqualTo(1999);
                    assertThat(json).extractingPath("$.adult").isEqualTo(false);
                    assertThat(json).extractingPath("$.description").isEqualTo("A ninja story.");
                    assertThat(json).extractingPath("$.authors[*].name").asArray()
                            .containsExactly("Kishimoto Masashi");
                    assertThat(json).extractingPath("$.artists[*].name").asArray()
                            .containsExactly("Ikemoto Mikio", "Kishimoto Masashi");
                    assertThat(json).extractingPath("$.genres[*].slug").asArray().containsExactly("action");
                    assertThat(json).extractingPath("$.libraryTags[*].slug").asArray().containsExactly("hq");
                    assertThat(json).extractingPath("$.parodies[*].name").asArray().containsExactly("Naruto");
                    assertThat(json).extractingPath("$.characters[0].name").isEqualTo("Rin");
                    assertThat(json).extractingPath("$.characters[0].parody.name").isEqualTo("Naruto");
                    assertThat(json).extractingPath("$.chapterCount").isEqualTo(4);
                    assertThat(json).extractingPath("$.missingChapters").asArray().containsExactly(3, 4);
                    assertThat(json).extractingPath("$.latestChapterAddedAt").isNotNull();
                    assertThat(json).extractingPath("$.ratingCount").isEqualTo(0);
                    assertThat(json).extractingPath("$.favoriteCount").isEqualTo(0);
                    assertThat(json).extractingPath("$.avgRating").isNull();
                });
    }

    @Test
    void relationsShowDerivedLabelsFromBothSides() {
        Media original = data.aMedia("Naruto").persist();
        Media sequel = data.aMedia("Boruto").persist();
        Media sideStory = data.aMedia("Naruto Gaiden").persist();
        Media spinOff = data.aMedia("Rock Lee SD").persist();
        data.aRelation(original, sequel, RelationType.SEQUEL);
        data.aRelation(original, sideStory, RelationType.SIDE_STORY);
        data.aRelation(original, spinOff, RelationType.SPIN_OFF);

        assertThat(get("/api/v1/media/{id}", original.getId()))
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.relations[*].label").asArray()
                            .containsExactly("SEQUEL", "SIDE_STORY", "SPIN_OFF");
                    assertThat(json).extractingPath("$.relations[*].media.title").asArray()
                            .containsExactly("Boruto", "Naruto Gaiden", "Rock Lee SD");
                });
        assertThat(get("/api/v1/media/{id}", sequel.getId()))
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.relations[0].label").isEqualTo("PREQUEL");
                    assertThat(json).extractingPath("$.relations[0].media.id").isEqualTo(original.getId().toString());
                });
        assertThat(get("/api/v1/media/{id}", sideStory.getId())).bodyJson()
                .extractingPath("$.relations[0].label").isEqualTo("MAIN_STORY");
        assertThat(get("/api/v1/media/{id}", spinOff.getId())).bodyJson()
                .extractingPath("$.relations[0].label").isEqualTo("ORIGINAL");
    }

    @Test
    void chapterListIsInNumberOrderWithoutPages() {
        Media media = data.aMedia("One Piece").chapters("10", "2", "0", "2.5").persist();

        assertThat(get("/api/v1/media/{id}/chapters", media.getId()))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$[*].number").asArray().containsExactly(0, 2, 2.5, 10);
                    assertThat(json).extractingPath("$[0].pageCount").isEqualTo(3);
                    assertThat(json).extractingPath("$[0].createdAt").isNotNull();
                    assertThat(json).doesNotHavePath("$[0].pages");
                });
    }

    @Test
    void chapterDetailIncludesOrderedPagesWithDimensions() throws Exception {
        Media media = data.aMedia("Bleach").chapters("12.5").persist();
        String chapterId = jdbc.queryForObject(
                "select id::text from chapter where media_id = ?", String.class, media.getId());

        assertThat(get("/api/v1/chapters/{id}", chapterId))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.mediaId").isEqualTo(media.getId().toString());
                    assertThat(json).extractingPath("$.number").isEqualTo(12.5);
                    assertThat(json).extractingPath("$.pageCount").isEqualTo(3);
                    assertThat(json).extractingPath("$.pages[*].f").asArray()
                            .containsExactly("001.jpg", "002.jpg", "003.jpg");
                    assertThat(json).extractingPath("$.pages[0].w").isEqualTo(800);
                    assertThat(json).extractingPath("$.pages[0].h").isEqualTo(1200);
                });
    }

    @Test
    void unknownIdsAreNotFound() {
        assertThat(get("/api/v1/media/{id}", UUID.randomUUID())).hasStatus(404);
        assertThat(get("/api/v1/media/{id}/chapters", UUID.randomUUID())).hasStatus(404);
        assertThat(get("/api/v1/chapters/{id}", UUID.randomUUID())).hasStatus(404);
        assertThat(get("/api/v1/media/not-a-uuid")).hasStatus(400);
    }
}

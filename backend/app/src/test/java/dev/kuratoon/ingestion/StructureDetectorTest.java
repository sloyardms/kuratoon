package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.ingestion.StructureDetector.ScannedChapter;
import dev.kuratoon.ingestion.StructureDetector.ScannedFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/spec/02-ingestion.md "Structure detection" and chapter-level errors. */
class StructureDetectorTest {

    @TempDir
    Path root;

    @Test
    void imagesDirectlyInTheFolderAreAOneShot() throws IOException {
        touch("002.jpg", "001.jpg", "source.txt", "cover.png", "Thumbs.db");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.oneShot()).isTrue();
        assertThat(scan.hasProblems()).isFalse();
        assertThat(scan.coverFile()).contains("cover.png");
        assertThat(scan.chapters()).singleElement().satisfies(c -> {
            assertThat(c.number()).isEqualByComparingTo("1");
            assertThat(c.title()).isNull();
            assertThat(c.folder()).isEqualTo("1");
            // The cover is not a page.
            assertThat(c.pages()).containsExactly("001.jpg", "002.jpg");
        });
    }

    @Test
    void subfoldersAreChaptersAndTopLevelImagesAreIgnoredExceptCover() throws IOException {
        touch("Chapter 2 - Two/001.jpg", "Chapter 0 - Prologue/1.png", "stray.jpg", "Cover.JPG", "kuratoon.yml");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.oneShot()).isFalse();
        assertThat(scan.hasProblems()).isFalse();
        assertThat(scan.coverFile()).contains("Cover.JPG");
        assertThat(scan.chapters()).extracting(ScannedChapter::folder).containsExactly("0", "2");
        assertThat(scan.chapters()).extracting(ScannedChapter::title).containsExactly("Prologue", "Two");
    }

    @Test
    void hiddenFoldersAreNotChapters() throws IOException {
        touch("Chapter 1/001.jpg", ".git/001.jpg", ".thumbs/x.jpg");
        assertThat(StructureDetector.scan(root).chapters()).hasSize(1);
    }

    @Test
    void nestedSubfoldersNeedAttention() throws IOException {
        touch("Chapter 1/001.jpg", "Chapter 1/extras/001.jpg");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.hasProblems()).isTrue();
        assertThat(scan.chapters().getFirst().problems()).singleElement().asString().contains("subfolders");
    }

    @Test
    void duplicateNumbersAreFlaggedOnEveryFolder() throws IOException {
        touch("Chapter 1/001.jpg", "Chapter 2/001.jpg", "chapter 02/001.jpg");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.hasProblems()).isTrue();
        assertThat(scan.chapters()).filteredOn(c -> !c.problems().isEmpty())
                .extracting(ScannedChapter::sourceFolder)
                .containsExactlyInAnyOrder("Chapter 2", "chapter 02");
        assertThat(scan.chapters().get(1).problems().getFirst()).contains("Chapter 2, chapter 02");
    }

    @Test
    void folderWithoutNumberNeedsAttentionAndSortsLast() throws IOException {
        touch("Extra/001.jpg", "Chapter 1/001.jpg");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.chapters()).extracting(ScannedChapter::sourceFolder).containsExactly("Chapter 1", "Extra");
        assertThat(scan.chapters().get(1).number()).isNull();
        assertThat(scan.chapters().get(1).problems()).containsExactly("No chapter number in the folder name.");
    }

    @Test
    void chapterWithZeroValidPagesNeedsAttention() throws IOException {
        touch("Chapter 1/001.jpg", "Chapter 2/Thumbs.db", "Chapter 2/notes.txt");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.chapters().get(1).problems()).containsExactly("No valid pages.");
    }

    @Test
    void emptyFolderNeedsAttention() throws IOException {
        touch("source.txt");
        ScannedFolder scan = StructureDetector.scan(root);
        assertThat(scan.hasProblems()).isTrue();
        assertThat(scan.chapters()).isEmpty();
    }

    private void touch(String... paths) throws IOException {
        for (String p : paths) {
            Path file = root.resolve(p);
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[] {1});
        }
    }
}

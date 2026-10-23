package dev.kuratoon.ingestion;

import dev.kuratoon.ingestion.ChapterNameParser.ParsedChapter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads an import folder: one-shot or series, chapter numbers and titles, page order and the
 * optional {@code cover.*} (docs/spec/02-ingestion.md "Structure detection"). Problems are
 * collected, never thrown, so the admin sees all of them in the chapter table at once.
 */
public final class StructureDetector {

    private StructureDetector() {}

    /**
     * @param sourceFolder folder inside the import folder; "" for a one-shot
     * @param number null when the name has no number
     * @param folder canonical library folder name; null without a number
     * @param pages page file names in reading order
     */
    public record ScannedChapter(
            String sourceFolder, BigDecimal number, String title, String folder, List<String> pages, List<String> problems) {

        public ScannedChapter {
            pages = List.copyOf(pages);
            problems = List.copyOf(problems);
        }
    }

    /** @param coverFile {@code cover.*} at the top level, if any */
    public record ScannedFolder(
            boolean oneShot, Optional<String> coverFile, List<ScannedChapter> chapters, List<String> problems) {

        public ScannedFolder {
            chapters = List.copyOf(chapters);
            problems = List.copyOf(problems);
        }

        /** True when anything at folder or chapter level needs the admin. */
        public boolean hasProblems() {
            return !problems.isEmpty() || chapters.stream().anyMatch(c -> !c.problems().isEmpty());
        }
    }

    public static ScannedFolder scan(Path folder) {
        List<Path> subfolders = new ArrayList<>();
        List<String> files = new ArrayList<>();
        list(folder, subfolders, files);

        Optional<String> cover = files.stream().filter(StructureDetector::isCover).sorted().findFirst();

        if (subfolders.isEmpty()) {
            List<String> pages = PageOrdering.sort(files.stream().filter(f -> !isCover(f)).toList());
            if (pages.isEmpty()) {
                return new ScannedFolder(true, cover, List.of(), List.of("No images or chapter folders found."));
            }
            ScannedChapter only = new ScannedChapter(
                    "", BigDecimal.ONE, null, ChapterNameParser.canonicalFolder(BigDecimal.ONE), pages, List.of());
            return new ScannedFolder(true, cover, List.of(only), List.of());
        }

        List<ScannedChapter> chapters = new ArrayList<>();
        for (Path sub : subfolders) {
            chapters.add(scanChapter(sub));
        }
        return new ScannedFolder(false, cover, markDuplicates(chapters), List.of());
    }

    private static ScannedChapter scanChapter(Path dir) {
        String name = dir.getFileName().toString();
        List<String> problems = new ArrayList<>();
        List<Path> nested = new ArrayList<>();
        List<String> files = new ArrayList<>();
        list(dir, nested, files);
        if (!nested.isEmpty()) {
            problems.add("Contains subfolders (" + nested.getFirst().getFileName()
                    + "); chapters must contain images only.");
        }
        List<String> pages = PageOrdering.sort(files);
        if (pages.isEmpty()) {
            problems.add("No valid pages.");
        }
        Optional<ParsedChapter> parsed = ChapterNameParser.parse(name);
        if (parsed.isEmpty()) {
            problems.add("No chapter number in the folder name.");
            return new ScannedChapter(name, null, null, null, pages, problems);
        }
        BigDecimal number = parsed.get().number();
        return new ScannedChapter(
                name, number, parsed.get().title(), ChapterNameParser.canonicalFolder(number), pages, problems);
    }

    private static List<ScannedChapter> markDuplicates(List<ScannedChapter> chapters) {
        Map<BigDecimal, List<String>> byNumber = new LinkedHashMap<>();
        for (ScannedChapter c : chapters) {
            if (c.number() != null) {
                byNumber.computeIfAbsent(c.number(), n -> new ArrayList<>()).add(c.sourceFolder());
            }
        }
        List<ScannedChapter> result = new ArrayList<>();
        for (ScannedChapter c : chapters) {
            List<String> same = c.number() == null ? List.of() : byNumber.get(c.number());
            if (same.size() > 1) {
                List<String> problems = new ArrayList<>(c.problems());
                problems.add("Chapter number " + ChapterNameParser.canonicalFolder(c.number())
                        + " appears in more than one folder: " + String.join(", ", same) + ".");
                c = new ScannedChapter(c.sourceFolder(), c.number(), c.title(), c.folder(), c.pages(), problems);
            }
            result.add(c);
        }
        result.sort(Comparator.comparing(ScannedChapter::number, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
                .thenComparing(ScannedChapter::sourceFolder));
        return result;
    }

    private static boolean isCover(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0
                && fileName.substring(0, dot).toLowerCase(Locale.ROOT).equals("cover")
                && PageOrdering.isPage(fileName);
    }

    /** Non-hidden subfolders and regular files of a folder, sorted by name. */
    private static void list(Path dir, List<Path> subfolders, List<String> files) {
        try (Stream<Path> entries = Files.list(dir)) {
            // By name, case-sensitively: Path order differs between operating systems.
            for (Path p : entries.sorted(Comparator.comparing(e -> e.getFileName().toString())).toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                if (Files.isDirectory(p)) {
                    subfolders.add(p);
                } else if (Files.isRegularFile(p)) {
                    files.add(name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

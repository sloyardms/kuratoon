package dev.kuratoon.ingestion;

import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.config.KuratoonProperties;
import dev.kuratoon.image.ImageProbe;
import dev.kuratoon.image.ImageProbe.ImageInfo;
import dev.kuratoon.image.ImageProbe.UnreadableImageException;
import dev.kuratoon.ingestion.ImportPlan.PlannedChapter;
import dev.kuratoon.ingestion.ImportPlan.PlannedCover;
import dev.kuratoon.ingestion.ImportPlan.PlannedPage;
import dev.kuratoon.ingestion.JobDetail.Attention;
import dev.kuratoon.ingestion.JobDetail.ChapterRow;
import dev.kuratoon.ingestion.JobDetail.ConflictChoice;
import dev.kuratoon.ingestion.JobDetail.Decisions;
import dev.kuratoon.ingestion.JobDetail.Duplicate;
import dev.kuratoon.ingestion.JobDetail.DuplicateChoice;
import dev.kuratoon.ingestion.KuratoonYaml.Document;
import dev.kuratoon.ingestion.KuratoonYaml.InvalidDocumentException;
import dev.kuratoon.ingestion.KuratoonYaml.ManifestDocument;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotChapter;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.ingestion.StructureDetector.ScannedChapter;
import dev.kuratoon.ingestion.StructureDetector.ScannedFolder;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaFields;
import dev.kuratoon.media.MediaMetadataWriter;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.media.MediaReadDtos.ChapterSummary;
import dev.kuratoon.mediatype.MediaType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RESOLVING (docs/spec/02-ingestion.md): reads the import folder and decides NEW / UPDATE /
 * RESTORE, parses the chapter table, reads every image header and checks duplicates and
 * conflicts. Has no side effects; the outcome is applied by the worker.
 */
@Service
public class ImportResolver {

    private final LibraryPaths paths;
    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final MediaMetadataWriter metadata;
    private final MetadataSource metadataSource;
    private final String defaultLanguage;

    public ImportResolver(
            LibraryPaths paths,
            MediaRepository media,
            ChapterRepository chapters,
            MediaMetadataWriter metadata,
            MetadataSource metadataSource,
            KuratoonProperties properties) {
        this.paths = paths;
        this.media = media;
        this.chapters = chapters;
        this.metadata = metadata;
        this.metadataSource = metadataSource;
        this.defaultLanguage = properties.defaultLanguage();
    }

    public sealed interface Outcome permits Ready, NeedsAttention, Failed {}

    public record Ready(ImportPlan plan, JobDetail detail) implements Outcome {}

    /** @param kind and @param mediaId when already known */
    public record NeedsAttention(String message, JobDetail detail, ImportKind kind, UUID mediaId) implements Outcome {}

    public record Failed(String message) implements Outcome {}

    @Transactional(readOnly = true)
    public Outcome resolve(ImportJob job) {
        Path src = paths.source(job.getSourceFolder());
        if (!Files.isDirectory(src)) {
            return new Failed("The import folder '" + job.getSourceFolder() + "' no longer exists.");
        }
        Context ctx = new Context(job, src);
        Document doc;
        try {
            doc = readDocument(src);
        } catch (InvalidDocumentException e) {
            return ctx.attention(Attention.INVALID_MANIFEST, e.getMessage());
        } catch (IOException e) {
            return new Failed("Cannot read the import folder: " + e.getMessage());
        }
        ctx.warnings.addAll(doc.warnings());
        try {
            return switch (doc) {
                case SnapshotDocument s -> resolveRestore(ctx, s);
                case ManifestDocument m -> resolveManifest(ctx, m);
            };
        } catch (IOException e) {
            return new Failed("Cannot read the import folder: " + e.getMessage());
        }
    }

    // --- NEW / UPDATE ---------------------------------------------------------------------------

    private Outcome resolveManifest(Context ctx, ManifestDocument manifest) throws IOException {
        ScannedFolder scan = StructureDetector.scan(ctx.src);
        for (ScannedChapter c : scan.chapters()) {
            ctx.rows.add(new ChapterRow(c.sourceFolder(), c.number(), c.title(), c.folder(), c.pages().size(), c.problems()));
        }
        if (scan.hasProblems()) {
            String first = scan.problems().isEmpty()
                    ? scan.chapters().stream().filter(c -> !c.problems().isEmpty()).findFirst()
                            .map(c -> folderLabel(c.sourceFolder()) + ": " + c.problems().getFirst()).orElse("")
                    : scan.problems().getFirst();
            return ctx.attention(Attention.CHAPTERS, "The chapter folders need attention. " + first);
        }

        List<PlannedChapter> planned = new ArrayList<>();
        for (ScannedChapter c : scan.chapters()) {
            Path dir = c.sourceFolder().isEmpty() ? ctx.src : ctx.src.resolve(c.sourceFolder());
            List<PlannedPage> pages = new ArrayList<>();
            for (int i = 0; i < c.pages().size(); i++) {
                Path file = dir.resolve(c.pages().get(i));
                Optional<ImageInfo> info = probe(ctx, file);
                if (info.isEmpty()) {
                    return ctx.attention(Attention.UNREADABLE_IMAGE, ctx.unreadable);
                }
                pages.add(new PlannedPage(file, PageOrdering.targetName(i, c.pages().get(i)), info.get().width(), info.get().height()));
            }
            planned.add(new PlannedChapter(c.number(), c.title(), c.folder(), pages, null, null));
        }

        Path html = ctx.src.resolve(KuratoonYaml.SOURCE_HTML);
        boolean hasHtml = Files.isRegularFile(html);
        String rawUrl = manifest.url();
        if (rawUrl == null && hasHtml) {
            rawUrl = canonicalUrl(html).orElse(null);
        }
        String url = null;
        if (rawUrl != null) {
            url = UrlNormalizer.normalize(rawUrl).orElse(null);
            if (url == null) {
                return ctx.attention(Attention.INVALID_MANIFEST, "'" + rawUrl + "' is not a valid http(s) URL.");
            }
        }
        ctx.url = url;

        Decisions decisions = ctx.job.detail().decisions();
        Optional<Media> target = url == null ? Optional.empty() : media.findBySourceUrl(url);
        if (target.isEmpty() && decisions.duplicate() == DuplicateChoice.UPDATE_EXISTING && decisions.duplicateTarget() != null) {
            target = media.findById(decisions.duplicateTarget());
        }
        if (target.isPresent()) {
            return resolveUpdate(ctx, target.get(), planned, decisions);
        }
        return resolveNew(ctx, manifest, planned, scan, url, hasHtml ? html : null, decisions);
    }

    private Outcome resolveUpdate(Context ctx, Media target, List<PlannedChapter> planned, Decisions decisions) {
        ctx.kind = ImportKind.UPDATE;
        ctx.mediaId = target.getId();
        Map<BigDecimal, ChapterSummary> existing = new HashMap<>();
        for (ChapterSummary s : chapters.findSummariesByMediaId(target.getId())) {
            existing.put(s.number().stripTrailingZeros(), s);
        }
        List<PlannedChapter> result = new ArrayList<>();
        boolean undecided = false;
        for (PlannedChapter c : planned) {
            ChapterSummary old = existing.get(c.number().stripTrailingZeros());
            if (old == null) {
                result.add(c);
                continue;
            }
            ctx.conflicts.add(new JobDetail.Conflict(c.number(), c.folder(), old.id(), old.pageCount(), c.pages().size()));
            ConflictChoice choice = decisions.conflicts().get(c.folder());
            if (choice == null) {
                undecided = true;
            } else if (choice == ConflictChoice.REPLACE) {
                result.add(new PlannedChapter(c.number(), c.title(), c.folder(), c.pages(), old.id(), null));
            }
        }
        if (undecided) {
            return ctx.attention(Attention.CONFLICT, ctx.conflicts.size() == 1
                    ? "Chapter " + ctx.conflicts.getFirst().folder() + " already exists in '" + target.getTitle()
                            + "'. Choose Skip or Replace."
                    : ctx.conflicts.size() + " chapters already exist in '" + target.getTitle()
                            + "'. Choose Skip or Replace for each.");
        }
        ImportPlan plan = new ImportPlan(ImportKind.UPDATE, target.getId(), null, Set.of(), null, null, result, null, null);
        return new Ready(plan, ctx.detail(null));
    }

    private Outcome resolveNew(
            Context ctx,
            ManifestDocument manifest,
            List<PlannedChapter> planned,
            ScannedFolder scan,
            String url,
            Path html,
            Decisions decisions) {
        ctx.kind = ImportKind.NEW;
        MediaFields manual = decisions.manualMetadata();
        MediaFields fields = manual != null ? manual.orElse(manifest.fields()) : manifest.fields();
        if (fields.title() == null) {
            // The folder name is only a fallback title.
            fields = fields.withTitle(ctx.job.getSourceFolder());
        }
        Set<String> lock = new LinkedHashSet<>(manifest.setFields());
        if (manual != null) {
            lock.addAll(setFields(manual));
        }
        String pluginId = null;
        boolean hasSource = url != null || html != null;
        if (!fields.isComplete() && manual == null && hasSource) {
            switch (metadataSource.lookup(url, html)) {
                case MetadataSource.Found found -> {
                    fields = fields.orElse(found.fields());
                    pluginId = found.pluginId();
                }
                case MetadataSource.NotAvailable na -> {
                    ctx.missing.addAll(fields.missingForCreate());
                    ctx.prefill = fields;
                    return ctx.attention(Attention.NO_PLUGIN, na.message());
                }
            }
        }
        if (!fields.isComplete()) {
            ctx.missing.addAll(fields.missingForCreate());
            ctx.prefill = fields;
            if (!hasSource && manual == null) {
                return ctx.attention(Attention.NO_METADATA_SOURCE,
                        "No metadata source: the folder has no URL, no source.html and the manifest is incomplete"
                                + " (missing " + String.join(", ", ctx.missing) + "). Fill in metadata manually.");
            }
            return ctx.attention(Attention.INCOMPLETE_METADATA,
                    "Metadata is incomplete: missing " + String.join(", ", ctx.missing) + ".");
        }
        Optional<MediaType> type = metadata.findType(fields.type());
        if (type.isEmpty()) {
            ctx.prefill = fields;
            ctx.missing.add("type");
            return ctx.attention(Attention.INCOMPLETE_METADATA, "Unknown media type '" + fields.type() + "'.");
        }
        if (fields.language() == null) {
            fields = fields.withLanguage(defaultLanguage);
        }

        if (decisions.duplicate() != DuplicateChoice.CREATE_NEW) {
            Set<String> titles = new LinkedHashSet<>();
            titles.add(fields.title().toLowerCase(Locale.ROOT));
            fields.titles().forEach(t -> titles.add(t.toLowerCase(Locale.ROOT)));
            List<Media> same = media.findSameTitle(type.get().getId(), titles);
            if (!same.isEmpty()) {
                Media candidate = same.getFirst();
                ctx.duplicate = new Duplicate(candidate.getId(), candidate.getTitle());
                ctx.prefill = fields;
                return ctx.attention(Attention.DUPLICATE, "'" + candidate.getTitle()
                        + "' with the same type is already in the library. Update it or create a new media?");
            }
        }

        PlannedCover cover = scan.coverFile()
                .map(name -> new PlannedCover(ctx.src.resolve(name), "cover." + PageOrdering.extension(name), null))
                .orElseGet(() -> {
                    PlannedPage first = planned.getFirst().pages().getFirst();
                    return new PlannedCover(first.source(), "cover." + PageOrdering.extension(first.target()), null);
                });
        if (scan.coverFile().isPresent() && probe(ctx, cover.source()).isEmpty()) {
            return ctx.attention(Attention.UNREADABLE_IMAGE, ctx.unreadable);
        }
        ImportPlan plan = new ImportPlan(ImportKind.NEW, null, fields, lock, url, pluginId, planned, cover, null);
        return new Ready(plan, ctx.detail(null));
    }

    // --- RESTORE --------------------------------------------------------------------------------

    private Outcome resolveRestore(Context ctx, SnapshotDocument s) throws IOException {
        ctx.kind = ImportKind.RESTORE;
        for (SnapshotChapter c : s.chapters()) {
            ctx.rows.add(new ChapterRow(c.folder(), c.number(), c.title(), c.folder(), c.pages().size(), List.of()));
        }
        if (media.existsById(s.id())) {
            ctx.mediaId = s.id();
            return ctx.attention(Attention.ALREADY_IN_LIBRARY,
                    "This snapshot's media (" + s.id() + ") is already in the library.");
        }
        String url = s.url() == null ? null : UrlNormalizer.normalize(s.url()).orElse(null);
        ctx.url = url;
        if (url != null) {
            Optional<Media> owner = media.findBySourceUrl(url);
            if (owner.isPresent()) {
                return ctx.attention(Attention.URL_IN_USE, "The snapshot's URL " + url + " belongs to '"
                        + owner.get().getTitle() + "' (" + owner.get().getId() + "), a different media.");
            }
        }
        MediaFields fields = s.fields().language() == null ? s.fields().withLanguage(defaultLanguage) : s.fields();
        if (metadata.findType(fields.type()).isEmpty()) {
            ctx.prefill = fields;
            return ctx.attention(Attention.INCOMPLETE_METADATA, "Unknown media type '" + fields.type() + "'.");
        }
        if (fields.authors().isEmpty() && fields.artists().isEmpty()) {
            ctx.prefill = fields;
            ctx.missing.add("authors or artists");
            return ctx.attention(Attention.INCOMPLETE_METADATA, "The snapshot has no author or artist.");
        }
        Set<BigDecimal> numbers = new HashSet<>();
        List<PlannedChapter> planned = new ArrayList<>();
        for (SnapshotChapter c : s.chapters()) {
            if (!isPlainName(c.folder())) {
                return ctx.attention(Attention.CHAPTERS, "Invalid chapter folder '" + c.folder() + "' in the snapshot.");
            }
            if (!numbers.add(c.number().stripTrailingZeros())) {
                return ctx.attention(Attention.CHAPTERS, "Chapter number " + c.folder() + " appears twice in the snapshot.");
            }
            List<PlannedPage> pages = new ArrayList<>();
            for (var p : c.pages()) {
                if (!isPlainName(p.f())) {
                    return ctx.attention(Attention.CHAPTERS, "Invalid page name '" + p.f() + "' in the snapshot.");
                }
                Path file = ctx.src.resolve(c.folder()).resolve(p.f());
                if (!Files.isRegularFile(file)) {
                    return ctx.attention(Attention.UNREADABLE_IMAGE, "Missing page " + c.folder() + "/" + p.f() + ".");
                }
                Optional<ImageInfo> info = probe(ctx, file);
                if (info.isEmpty()) {
                    return ctx.attention(Attention.UNREADABLE_IMAGE, ctx.unreadable);
                }
                pages.add(new PlannedPage(file, p.f(), info.get().width(), info.get().height()));
            }
            planned.add(new PlannedChapter(c.number(), c.title(), c.folder(), pages, null, c.createdAt()));
        }
        PlannedCover cover = null;
        if (s.cover() != null && isPlainName(s.cover().file()) && Files.isRegularFile(ctx.src.resolve(s.cover().file()))) {
            cover = new PlannedCover(ctx.src.resolve(s.cover().file()), s.cover().file(), s.cover().crop());
            if (probe(ctx, cover.source()).isEmpty()) {
                return ctx.attention(Attention.UNREADABLE_IMAGE, ctx.unreadable);
            }
        } else {
            if (s.cover() != null) {
                ctx.warnings.add("Cover " + s.cover().file() + " is missing; using the first page.");
            }
            PlannedPage first = planned.getFirst().pages().getFirst();
            cover = new PlannedCover(first.source(), "cover." + PageOrdering.extension(first.target()), null);
        }
        ImportPlan plan = new ImportPlan(ImportKind.RESTORE, s.id(), fields, Set.copyOf(s.locked()), url, s.plugin(),
                planned, cover, s);
        return new Ready(plan, ctx.detail(null));
    }

    // --- helpers --------------------------------------------------------------------------------

    private static Document readDocument(Path src) throws IOException, InvalidDocumentException {
        Path yml = src.resolve(KuratoonYaml.FILE_NAME);
        if (Files.isRegularFile(yml)) {
            return KuratoonYaml.parse(Files.readString(yml, StandardCharsets.UTF_8));
        }
        Path txt = src.resolve(KuratoonYaml.SOURCE_TXT);
        if (Files.isRegularFile(txt)) {
            return KuratoonYaml.fromSourceTxt(Files.readString(txt, StandardCharsets.UTF_8));
        }
        return ManifestDocument.EMPTY;
    }

    /** {@code <link rel="canonical">}, else {@code <meta property="og:url">} (docs/spec/03). */
    static Optional<String> canonicalUrl(Path html) throws IOException {
        var doc = Jsoup.parse(html.toFile(), null);
        Element canonical = doc.selectFirst("link[rel=canonical][href]");
        if (canonical != null && !canonical.attr("href").isBlank()) {
            return Optional.of(canonical.attr("href"));
        }
        Element og = doc.selectFirst("meta[property=og:url][content]");
        return og == null || og.attr("content").isBlank() ? Optional.empty() : Optional.of(og.attr("content"));
    }

    private static Set<String> setFields(MediaFields f) {
        Set<String> set = new LinkedHashSet<>();
        if (f.title() != null) set.add("title");
        if (!f.titles().isEmpty()) set.add("titles");
        if (f.description() != null) set.add("description");
        if (f.type() != null) set.add("type");
        if (f.language() != null) set.add("language");
        if (f.status() != null) set.add("status");
        if (f.year() != null) set.add("year");
        if (f.adult() != null) set.add("adult");
        if (!f.authors().isEmpty()) set.add("authors");
        if (!f.artists().isEmpty()) set.add("artists");
        if (!f.genres().isEmpty()) set.add("genres");
        if (!f.tags().isEmpty()) set.add("tags");
        if (!f.parodies().isEmpty()) set.add("parodies");
        if (!f.characters().isEmpty()) set.add("characters");
        return set;
    }

    /** A single path segment that cannot escape its folder. */
    static boolean isPlainName(String name) {
        return name != null && !name.isBlank() && !name.startsWith(".") && !name.contains("/") && !name.contains("\\")
                && !name.contains(":");
    }

    private static String folderLabel(String sourceFolder) {
        return sourceFolder.isEmpty() ? "Pages" : sourceFolder;
    }

    private static Optional<ImageInfo> probe(Context ctx, Path file) {
        try {
            return Optional.of(ImageProbe.probe(file));
        } catch (UnreadableImageException | IOException e) {
            ctx.unreadable = "Unreadable image " + ctx.src.relativize(file).toString().replace('\\', '/') + ": "
                    + e.getMessage() + ".";
            String chapterFolder = ctx.src.relativize(file).getNameCount() > 1
                    ? ctx.src.relativize(file).getName(0).toString()
                    : "";
            for (int i = 0; i < ctx.rows.size(); i++) {
                ChapterRow row = ctx.rows.get(i);
                if (row.sourceFolder().equals(chapterFolder)) {
                    List<String> warnings = new ArrayList<>(row.warnings());
                    warnings.add(ctx.unreadable);
                    ctx.rows.set(i, new ChapterRow(row.sourceFolder(), row.number(), row.title(), row.folder(),
                            row.pageCount(), warnings));
                }
            }
            return Optional.empty();
        }
    }

    /** Collects what the Imports page shows while resolving. */
    private static final class Context {
        final ImportJob job;
        final Path src;
        final List<ChapterRow> rows = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        final List<JobDetail.Conflict> conflicts = new ArrayList<>();
        MediaFields prefill;
        Duplicate duplicate;
        String url;
        String unreadable;
        ImportKind kind;
        UUID mediaId;

        Context(ImportJob job, Path src) {
            this.job = job;
            this.src = src;
        }

        JobDetail detail(Attention attention) {
            return job.detail().resolved(attention, url, rows, warnings, missing, prefill, conflicts, duplicate);
        }

        NeedsAttention attention(Attention reason, String message) {
            return new NeedsAttention(message, detail(reason), kind, mediaId);
        }
    }
}

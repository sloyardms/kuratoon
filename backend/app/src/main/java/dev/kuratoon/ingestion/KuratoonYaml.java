package dev.kuratoon.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.media.MediaFields;
import dev.kuratoon.media.MediaFields.CharacterEntry;
import dev.kuratoon.media.CoverCrop;
import dev.kuratoon.media.PublicationStatus;
import dev.kuratoon.media.RelationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * {@code kuratoon.yml}: the manifest written by a person and the snapshot (sidecar) written by the
 * app, plus {@code source.txt} (docs/spec/02-ingestion.md).
 */
public final class KuratoonYaml {

    public static final String FILE_NAME = "kuratoon.yml";
    public static final String SOURCE_TXT = "source.txt";
    public static final String SOURCE_HTML = "source.html";
    static final int SCHEMA_VERSION = 1;

    private static final Set<String> MANIFEST_KEYS;
    private static final Set<String> SNAPSHOT_KEYS;

    static {
        Set<String> manifest = new LinkedHashSet<>(List.of("kuratoon", "kind", "url"));
        manifest.addAll(MediaFields.FIELD_NAMES);
        MANIFEST_KEYS = Set.copyOf(manifest);
        Set<String> snapshot = new LinkedHashSet<>(manifest);
        snapshot.addAll(List.of("id", "plugin", "locked", "cover", "created_at", "chapters", "relations", "moments"));
        SNAPSHOT_KEYS = Set.copyOf(snapshot);
    }

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .build();

    private KuratoonYaml() {}

    /** A manifest or snapshot that cannot be used; the message is shown to the admin. */
    public static class InvalidDocumentException extends Exception {
        public InvalidDocumentException(String message) {
            super(message);
        }
    }

    public sealed interface Document permits ManifestDocument, SnapshotDocument {
        List<String> warnings();
    }

    /**
     * @param url as written (not normalized), or null
     * @param setFields manifest keys present in the file; they become locked fields
     */
    public record ManifestDocument(String url, MediaFields fields, Set<String> setFields, List<String> warnings)
            implements Document {

        public static final ManifestDocument EMPTY = new ManifestDocument(null, MediaFields.EMPTY, Set.of(), List.of());
    }

    public record SnapshotDocument(
            UUID id,
            String url,
            String plugin,
            MediaFields fields,
            List<String> locked,
            Cover cover,
            Instant createdAt,
            List<SnapshotChapter> chapters,
            List<SnapshotRelation> relations,
            List<String> warnings)
            implements Document {}

    /** @param crop null when the cover has no crop */
    public record Cover(String file, CoverCrop crop) {}

    public record SnapshotChapter(BigDecimal number, String title, String folder, Instant createdAt, List<PageEntry> pages) {}

    public record SnapshotRelation(RelationType type, UUID mediaId) {}

    // --- reading --------------------------------------------------------------------------------

    public static Document parse(String text) throws InvalidDocumentException {
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (JacksonException e) {
            throw new InvalidDocumentException("kuratoon.yml is not valid YAML: " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw new InvalidDocumentException("kuratoon.yml must be a mapping of keys to values.");
        }
        JsonNode version = root.get("kuratoon");
        if (version == null || !version.isIntegralNumber()) {
            throw new InvalidDocumentException("kuratoon.yml must start with 'kuratoon: 1'.");
        }
        if (version.asInt() != SCHEMA_VERSION) {
            throw new InvalidDocumentException("kuratoon.yml schema version " + version.asInt()
                    + " is not supported (expected " + SCHEMA_VERSION + ").");
        }
        JsonNode kindNode = root.get("kind");
        String kind = kindNode == null || kindNode.isNull() ? "manifest" : kindNode.asString();
        boolean snapshot = switch (kind) {
            case "manifest" -> false;
            case "snapshot" -> true;
            default -> throw new InvalidDocumentException("Unknown kind '" + kind + "' (expected manifest or snapshot).");
        };

        List<String> warnings = new ArrayList<>();
        Set<String> present = new LinkedHashSet<>(root.propertyNames());
        for (String key : present) {
            if (!(snapshot ? SNAPSHOT_KEYS : MANIFEST_KEYS).contains(key)) {
                warnings.add("Unknown key '" + key + "' ignored.");
            }
        }

        Raw raw;
        try {
            raw = MAPPER.treeToValue(root, Raw.class);
        } catch (JacksonException e) {
            throw new InvalidDocumentException("kuratoon.yml has an invalid value: " + e.getOriginalMessage());
        }
        MediaFields fields = raw.fields();
        if (!snapshot) {
            Set<String> setFields = new LinkedHashSet<>();
            for (String name : MediaFields.FIELD_NAMES) {
                JsonNode value = root.get(name);
                if (value != null && !value.isNull()) {
                    setFields.add(name);
                }
            }
            return new ManifestDocument(blankToNull(raw.url), fields, Set.copyOf(setFields), List.copyOf(warnings));
        }
        return snapshot(raw, fields, warnings);
    }

    private static SnapshotDocument snapshot(Raw raw, MediaFields fields, List<String> warnings)
            throws InvalidDocumentException {
        if (raw.id == null) {
            throw new InvalidDocumentException("Snapshot has no id.");
        }
        if (fields.title() == null || fields.type() == null) {
            throw new InvalidDocumentException("Snapshot must have a title and a type.");
        }
        if (raw.createdAt == null) {
            throw new InvalidDocumentException("Snapshot has no created_at.");
        }
        List<SnapshotChapter> chapters = new ArrayList<>();
        for (RawChapter c : raw.chapters == null ? List.<RawChapter>of() : raw.chapters) {
            if (c == null || c.number == null || c.folder == null || c.createdAt == null) {
                throw new InvalidDocumentException("Every snapshot chapter needs number, folder and created_at.");
            }
            if (c.pages == null || c.pages.isEmpty()) {
                throw new InvalidDocumentException("Snapshot chapter " + c.folder + " has no pages.");
            }
            for (PageEntry p : c.pages) {
                if (p == null || p.f() == null || p.w() <= 0 || p.h() <= 0) {
                    throw new InvalidDocumentException("Snapshot chapter " + c.folder + " has a page without f, w, h.");
                }
            }
            chapters.add(new SnapshotChapter(
                    c.number.stripTrailingZeros(), blankToNull(c.title), c.folder, c.createdAt, List.copyOf(c.pages)));
        }
        if (chapters.isEmpty()) {
            throw new InvalidDocumentException("Snapshot has no chapters.");
        }
        List<SnapshotRelation> relations = new ArrayList<>();
        for (RawRelation r : raw.relations == null ? List.<RawRelation>of() : raw.relations) {
            if (r == null || r.type == null || r.mediaId == null) {
                throw new InvalidDocumentException("Every snapshot relation needs type and media_id.");
            }
            relations.add(new SnapshotRelation(r.type, r.mediaId));
        }
        if (raw.moments != null && !raw.moments.isEmpty()) {
            warnings.add("Moments in the snapshot are not restored yet (" + raw.moments.size() + " skipped).");
        }
        Cover cover = raw.cover == null || blankToNull(raw.cover.file) == null
                ? null
                : new Cover(raw.cover.file.strip(), raw.cover.crop);
        List<String> locked = raw.locked == null
                ? List.of()
                : raw.locked.stream().filter(MediaFields.FIELD_NAMES::contains).distinct().toList();
        return new SnapshotDocument(raw.id, blankToNull(raw.url), blankToNull(raw.plugin), fields, locked, cover,
                raw.createdAt, List.copyOf(chapters), List.copyOf(relations), List.copyOf(warnings));
    }

    /** {@code source.txt}: the first non-blank line is the URL; equivalent to a url-only manifest. */
    public static ManifestDocument fromSourceTxt(String text) throws InvalidDocumentException {
        for (String line : text.lines().toList()) {
            String url = line.strip();
            if (!url.isEmpty() && !url.startsWith("#")) {
                return new ManifestDocument(url, MediaFields.EMPTY, Set.of(), List.of());
            }
        }
        throw new InvalidDocumentException("source.txt is empty.");
    }

    // --- writing --------------------------------------------------------------------------------

    /** Serializes a snapshot in the key order of docs/spec/02-ingestion.md. */
    public static String write(SnapshotDocument s) {
        MediaFields f = s.fields();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kuratoon", SCHEMA_VERSION);
        out.put("kind", "snapshot");
        out.put("id", s.id().toString());
        out.put("url", s.url());
        out.put("plugin", s.plugin());
        out.put("type", f.type());
        out.put("language", f.language());
        out.put("adult", f.adult());
        out.put("title", f.title());
        out.put("titles", f.titles());
        out.put("status", f.status() == null ? null : f.status().name());
        out.put("year", f.year());
        out.put("description", f.description());
        out.put("authors", f.authors());
        out.put("artists", f.artists());
        out.put("genres", f.genres());
        out.put("tags", f.tags());
        out.put("parodies", f.parodies());
        out.put("characters", f.characters().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("parody", c.parody());
            return m;
        }).toList());
        out.put("locked", s.locked());
        if (s.cover() != null) {
            Map<String, Object> cover = new LinkedHashMap<>();
            cover.put("file", s.cover().file());
            cover.put("crop", s.cover().crop());
            out.put("cover", cover);
        }
        out.put("created_at", s.createdAt().toString());
        out.put("chapters", s.chapters().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("number", new BigDecimal(ChapterNameParser.canonicalFolder(c.number())));
            m.put("title", c.title());
            m.put("folder", c.folder());
            m.put("created_at", c.createdAt().toString());
            m.put("pages", c.pages());
            return m;
        }).toList());
        out.put("relations", s.relations().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.type().name());
            m.put("media_id", r.mediaId().toString());
            return m;
        }).toList());
        out.put("moments", List.of());
        return MAPPER.writeValueAsString(out);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // --- raw binding ----------------------------------------------------------------------------

    record Raw(
            String url,
            UUID id,
            String plugin,
            String title,
            List<String> titles,
            String description,
            String type,
            String language,
            PublicationStatus status,
            Integer year,
            Boolean adult,
            List<String> authors,
            List<String> artists,
            List<String> genres,
            List<String> tags,
            List<String> parodies,
            List<CharacterEntry> characters,
            List<String> locked,
            RawCover cover,
            @JsonProperty("created_at") Instant createdAt,
            List<RawChapter> chapters,
            List<RawRelation> relations,
            List<Object> moments) {

        MediaFields fields() {
            return new MediaFields(title, titles, description, type, language, status, year, adult, authors, artists,
                    genres, tags, parodies, characters);
        }
    }

    record RawCover(String file, CoverCrop crop) {}

    record RawChapter(
            BigDecimal number, String title, String folder, @JsonProperty("created_at") Instant createdAt, List<PageEntry> pages) {}

    record RawRelation(RelationType type, @JsonProperty("media_id") UUID mediaId) {}
}

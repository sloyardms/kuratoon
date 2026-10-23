package dev.kuratoon.media;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterCounters;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.image.ImageProbe;
import dev.kuratoon.image.ImageProbe.ImageInfo;
import dev.kuratoon.image.ImageProbe.UnreadableImageException;
import dev.kuratoon.image.ImageToolException;
import dev.kuratoon.image.ThumbnailService;
import dev.kuratoon.ingestion.LibraryPaths;
import dev.kuratoon.ingestion.PageOrdering;
import dev.kuratoon.ingestion.SidecarService;
import dev.kuratoon.media.MediaAdminDtos.CoverResult;
import dev.kuratoon.media.MediaAdminDtos.CoverView;
import dev.kuratoon.media.MediaAdminDtos.MediaEditView;
import dev.kuratoon.media.MediaAdminDtos.MediaRow;
import dev.kuratoon.media.MediaAdminDtos.MediaUpdateRequest;
import dev.kuratoon.media.MediaAdminDtos.RelationRequest;
import dev.kuratoon.media.MediaAdminDtos.RelationView;
import dev.kuratoon.media.MediaReadDtos.MediaTypeRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileSystemUtils;

/**
 * Admin media edits: metadata with field locking, relations, cover crop and replacement, chapter
 * and media deletion. Every change is audited and schedules a sidecar write.
 */
@Service
public class MediaAdminService {

    private static final Logger log = LoggerFactory.getLogger(MediaAdminService.class);
    private static final String TARGET = "media";

    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final ChapterCounters counters;
    private final MediaRelationRepository relations;
    private final MediaMetadataWriter metadata;
    private final ThumbnailService thumbnails;
    private final SidecarService sidecars;
    private final LibraryPaths paths;
    private final AuditService audit;

    public MediaAdminService(
            MediaRepository media,
            ChapterRepository chapters,
            ChapterCounters counters,
            MediaRelationRepository relations,
            MediaMetadataWriter metadata,
            ThumbnailService thumbnails,
            SidecarService sidecars,
            LibraryPaths paths,
            AuditService audit) {
        this.media = media;
        this.chapters = chapters;
        this.counters = counters;
        this.relations = relations;
        this.metadata = metadata;
        this.thumbnails = thumbnails;
        this.sidecars = sidecars;
        this.paths = paths;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OffsetPage<MediaRow> list(String q, UUID typeId, PageParams paging) {
        Sort sort = Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"));
        return OffsetPage.of(media.searchAdmin(Queries.containsPattern(q), typeId, paging.toPageRequest(sort)),
                m -> new MediaRow(m.getId(), m.getTitle(), typeRef(m), m.getChapterCount(),
                        m.getLatestChapterAddedAt(), m.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public MediaEditView get(UUID id) {
        return view(find(id));
    }

    /** Applies the edited fields and locks each of them (docs/spec/02-ingestion.md). */
    @Transactional
    public MediaEditView update(UUID id, MediaUpdateRequest request) {
        Media m = find(id);
        Set<String> fields = new LinkedHashSet<>(request.fields());
        for (String f : fields) {
            if (!MediaFields.FIELD_NAMES.contains(f)) {
                throw new BadRequestException("Unknown field '" + f + "'.");
            }
        }
        MediaFields values = request.values();
        if (fields.contains("title") && values.title() == null) {
            throw new BadRequestException("The title cannot be empty.");
        }
        if (fields.contains("type") && values.type() == null) {
            throw new BadRequestException("Choose a media type.");
        }
        if (fields.contains("language") && values.language() == null) {
            throw new BadRequestException("The language cannot be empty.");
        }
        if (fields.contains("year") && values.year() != null && (values.year() < 1000 || values.year() > 9999)) {
            throw new BadRequestException("The year must have four digits.");
        }
        metadata.apply(m, values, fields);
        MediaInvariants.requireAuthorOrArtist(m);
        m.lockFields(fields);
        audit.record("MEDIA_UPDATED", TARGET, id, Map.of("fields", List.copyOf(fields)));
        sidecars.scheduleWrite(id);
        return view(m);
    }

    /** Sets the locked fields exactly (lock and unlock individual fields). */
    @Transactional
    public MediaEditView setLockedFields(UUID id, List<String> fields) {
        Media m = find(id);
        for (String f : fields) {
            if (!MediaFields.FIELD_NAMES.contains(f)) {
                throw new BadRequestException("Unknown field '" + f + "'.");
            }
        }
        List<String> before = List.copyOf(m.getLockedFields());
        m.setLockedFields(fields);
        if (!before.equals(m.getLockedFields())) {
            audit.record("MEDIA_LOCKS_CHANGED", TARGET, id, Map.of("locked", List.copyOf(m.getLockedFields())));
            sidecars.scheduleWrite(id);
        }
        return view(m);
    }

    /** Replaces the relations stored on this media ({@code mediaId} is a {@code type} of it). */
    @Transactional
    public MediaEditView setRelations(UUID id, List<RelationRequest> wanted) {
        Media m = find(id);
        Set<UUID> targets = new HashSet<>();
        for (RelationRequest r : wanted) {
            if (r.mediaId().equals(id)) {
                throw new BadRequestException("A media cannot be related to itself.");
            }
            if (!targets.add(r.mediaId())) {
                throw new BadRequestException("Each related media can appear only once.");
            }
        }
        List<MediaRelation> current = relations.findInvolving(id).stream()
                .filter(r -> r.getFrom().getId().equals(id))
                .toList();
        for (MediaRelation r : current) {
            boolean keep = wanted.stream().anyMatch(w -> w.mediaId().equals(r.getTo().getId()) && w.type() == r.getType());
            if (!keep) {
                relations.delete(r);
            }
        }
        relations.flush();
        for (RelationRequest w : wanted) {
            boolean exists = current.stream().anyMatch(r -> r.getTo().getId().equals(w.mediaId()) && r.getType() == w.type());
            if (!exists) {
                Media to = media.findById(w.mediaId())
                        .orElseThrow(() -> new BadRequestException("Related media " + w.mediaId() + " does not exist."));
                boolean reverse = relations.findInvolving(id).stream()
                        .anyMatch(r -> r.getFrom().getId().equals(w.mediaId()) && r.getTo().getId().equals(id));
                if (reverse) {
                    throw new ConflictException("'" + to.getTitle() + "' already has a relation to this media.");
                }
                relations.save(new MediaRelation(m, to, w.type()));
            }
        }
        audit.record("MEDIA_RELATIONS_CHANGED", TARGET, id, Map.of("count", wanted.size()));
        sidecars.scheduleWrite(id);
        return view(m);
    }

    /** Saves the crop, bumps {@code cover_version} and regenerates thumbnails from the original. */
    @Transactional
    public CoverResult setCoverCrop(UUID id, CoverCrop crop) {
        Media m = find(id);
        if (m.getCoverFile() == null) {
            throw new ConflictException("This media has no cover.");
        }
        validate(crop);
        m.setCoverCrop(crop);
        audit.record("MEDIA_COVER_CROPPED", TARGET, id, crop == null ? Map.of() : Map.of(
                "x", crop.x(), "y", crop.y(), "w", crop.w(), "h", crop.h()));
        sidecars.scheduleWrite(id);
        String warning = regenerate(m);
        return new CoverResult(cover(m), warning);
    }

    /**
     * Replaces the original cover with an uploaded image (crop reset). The file is checked with
     * the image header probe before it replaces anything.
     */
    @Transactional
    public CoverResult replaceCover(UUID id, String originalName, InputStream content) throws IOException {
        Media m = find(id);
        String ext = extensionOf(originalName);
        Path dir = paths.mediaDir(id);
        if (!Files.isDirectory(dir)) {
            throw new ConflictException("The library folder of this media is missing.");
        }
        Path upload = Files.createTempFile(dir, ".upload-", "." + ext);
        try {
            Files.copy(content, upload, StandardCopyOption.REPLACE_EXISTING);
            try {
                ImageProbe.probe(upload);
            } catch (UnreadableImageException e) {
                throw new BadRequestException("The uploaded file is not a readable image: " + e.getMessage() + ".");
            }
            String newName = "cover." + ext;
            String oldName = m.getCoverFile();
            Files.move(upload, dir.resolve(newName), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (oldName != null && !oldName.equals(newName) && isCoverName(oldName)) {
                Files.deleteIfExists(dir.resolve(oldName));
            }
            m.setCover(newName, null);
        } finally {
            Files.deleteIfExists(upload);
        }
        audit.record("MEDIA_COVER_REPLACED", TARGET, id, Map.of("file", m.getCoverFile()));
        sidecars.scheduleWrite(id);
        String warning = regenerate(m);
        return new CoverResult(cover(m), warning);
    }

    /** The original cover file, for the admin crop tool. */
    @Transactional(readOnly = true)
    public Path originalCover(UUID id) {
        Media m = find(id);
        if (m.getCoverFile() == null) {
            throw new NotFoundException("This media has no cover.");
        }
        Path file = paths.mediaDir(id).resolve(m.getCoverFile());
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("The cover file is missing.");
        }
        return file;
    }

    /** Deletes the chapter row (progress, favorites and moments cascade), then its folder. */
    @Transactional
    public MediaEditView deleteChapter(UUID mediaId, UUID chapterId) {
        Media m = find(mediaId);
        Chapter chapter = chapters.findById(chapterId)
                .filter(c -> c.getMedia().getId().equals(mediaId))
                .orElseThrow(() -> new NotFoundException("Chapter not found."));
        String folder = chapter.getFolder();
        chapters.delete(chapter);
        counters.recalculate(m);
        audit.record("CHAPTER_DELETED", "chapter", chapterId, Map.of(
                "mediaId", mediaId.toString(), "folder", folder));
        sidecars.scheduleWrite(mediaId);
        afterCommit(() -> deleteQuietly(paths.mediaDir(mediaId).resolve(folder)));
        return view(m);
    }

    /**
     * Deletes the media. The library folder is renamed to {@code .deleted-<id>} first (so a rebuild
     * from disk can never bring it back) and removed after the commit, with its thumbnails.
     */
    @Transactional
    public void delete(UUID id) {
        Media m = find(id);
        Path dir = paths.mediaDir(id);
        Path deleted = paths.deletedMediaDir(id);
        if (Files.exists(dir)) {
            try {
                Files.move(dir, deleted, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new ConflictException("Could not move the library folder aside: " + e.getMessage());
            }
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteQuietly(deleted);
                    try {
                        thumbnails.deleteCoverThumbs(id);
                    } catch (IOException e) {
                        log.warn("Could not delete thumbnails of media {}: {}", id, e.toString());
                    }
                } else if (Files.exists(deleted)) {
                    try {
                        Files.move(deleted, dir, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        log.error("Could not restore {} after a failed delete", dir, e);
                    }
                }
            }
        });
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", m.getTitle());
        details.put("chapters", m.getChapterCount());
        media.delete(m);
        audit.record("MEDIA_DELETED", TARGET, id, details);
    }

    // --- helpers --------------------------------------------------------------------------------

    private String regenerate(Media m) {
        Path original = paths.mediaDir(m.getId()).resolve(m.getCoverFile());
        try {
            thumbnails.generateCover(m.getId(), original, m.getCoverCrop());
            return null;
        } catch (ImageToolException e) {
            log.warn("Thumbnails for media {} failed: {}", m.getId(), e.getMessage());
            return "Saved, but the thumbnails could not be regenerated: " + e.getMessage();
        }
    }

    private static void validate(CoverCrop crop) {
        if (crop == null) {
            return;
        }
        boolean ok = crop.x() >= 0 && crop.y() >= 0 && crop.w() > 0 && crop.h() > 0
                && crop.x() + crop.w() <= 1.0001 && crop.y() + crop.h() <= 1.0001;
        if (!ok) {
            throw new BadRequestException("The crop must lie inside the image (normalized 0–1).");
        }
    }

    private static String extensionOf(String name) {
        String ext = name == null ? "" : PageOrdering.extension(name);
        if (!PageOrdering.PAGE_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("Upload a jpg, png, webp, avif or gif image.");
        }
        return ext;
    }

    private static boolean isCoverName(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("cover.");
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void deleteQuietly(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.toString());
        }
    }

    private Media find(UUID id) {
        return media.findById(id).orElseThrow(() -> new NotFoundException("Media not found."));
    }

    private static MediaTypeRef typeRef(Media m) {
        var t = m.getMediaType();
        return new MediaTypeRef(t.getId(), t.getName(), t.getSlug(), t.getColor());
    }

    private CoverView cover(Media m) {
        if (m.getCoverFile() == null) {
            return new CoverView(null, null, m.getCoverVersion(), 0, 0, false);
        }
        int w = 0;
        int h = 0;
        try {
            ImageInfo info = ImageProbe.probe(paths.mediaDir(m.getId()).resolve(m.getCoverFile()));
            w = info.width();
            h = info.height();
        } catch (IOException | UnreadableImageException e) {
            // Reported by the integrity scan.
        }
        return new CoverView(m.getCoverFile(), m.getCoverCrop(), m.getCoverVersion(), w, h,
                thumbnails.coverThumbsExist(m.getId()));
    }

    private MediaEditView view(Media m) {
        List<RelationView> relationViews = new ArrayList<>();
        for (MediaRelation r : relations.findInvolving(m.getId())) {
            boolean outgoing = r.getFrom().getId().equals(m.getId());
            Media other = outgoing ? r.getTo() : r.getFrom();
            relationViews.add(new RelationView(r.getId(), r.getType(), outgoing ? "OUTGOING" : "INCOMING",
                    outgoing ? RelationLabel.forward(r.getType()) : RelationLabel.inverse(r.getType()),
                    other.getId(), other.getTitle()));
        }
        return new MediaEditView(m.getId(), metadata.read(m), m.getLockedFields(), m.getSourceUrl(), m.getPluginId(),
                cover(m), relationViews, chapters.findSummariesByMediaId(m.getId()), m.getCreatedAt(), m.getUpdatedAt());
    }
}

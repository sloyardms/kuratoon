package dev.kuratoon.ingestion;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterCounters;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.ingestion.ImportFaults.Step;
import dev.kuratoon.ingestion.ImportPlan.PlannedChapter;
import dev.kuratoon.ingestion.JobDetail.CommitPlan;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotRelation;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaFields;
import dev.kuratoon.media.MediaInvariants;
import dev.kuratoon.media.MediaMetadataWriter;
import dev.kuratoon.media.MediaRelation;
import dev.kuratoon.media.MediaRelationRepository;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.mediatype.MediaType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commit step 3: every library row of an import in one transaction, together with the job's
 * {@code committed_at} and the {@link CommitPlan} that startup recovery rolls forward.
 */
@Service
public class ImportCommitter {

    private final ImportJobRepository jobs;
    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final ChapterCounters counters;
    private final MediaRelationRepository relations;
    private final MediaMetadataWriter metadata;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final ImportFaults faults;
    private final Clock clock;

    public ImportCommitter(
            ImportJobRepository jobs,
            MediaRepository media,
            ChapterRepository chapters,
            ChapterCounters counters,
            MediaRelationRepository relations,
            MediaMetadataWriter metadata,
            AuditService audit,
            JdbcClient jdbc,
            ImportFaults faults,
            Clock clock) {
        this.jobs = jobs;
        this.media = media;
        this.chapters = chapters;
        this.counters = counters;
        this.relations = relations;
        this.metadata = metadata;
        this.audit = audit;
        this.jdbc = jdbc;
        this.faults = faults;
        this.clock = clock;
    }

    /** @return the media id */
    @Transactional
    public UUID commit(UUID jobId, ImportPlan plan) {
        ImportJob job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Import job not found."));
        Instant now = clock.instant();
        List<String> warnings = new ArrayList<>();
        Media m = switch (plan.kind()) {
            case NEW -> createNew(plan);
            case RESTORE -> restore(plan, warnings);
            case UPDATE -> media.findById(plan.targetMediaId())
                    .orElseThrow(() -> new NotFoundException("The media to update no longer exists."));
        };

        List<String> folders = new ArrayList<>();
        List<String> replaced = new ArrayList<>();
        for (PlannedChapter c : plan.chapters()) {
            List<PageEntry> pages = c.pages().stream().map(p -> new PageEntry(p.target(), p.width(), p.height())).toList();
            folders.add(c.folder());
            if (c.replaces() != null) {
                Chapter existing = chapters.findById(c.replaces())
                        .orElseThrow(() -> new NotFoundException("The chapter to replace no longer exists."));
                existing.replacePages(pages);
                clampProgress(existing.getId(), pages.size());
                replaced.add(c.folder());
            } else {
                chapters.save(new Chapter(m, c.number(), c.title(), c.folder(), pages, c.createdAt() != null ? c.createdAt() : now));
            }
        }
        counters.recalculate(m);

        if (plan.kind() == ImportKind.RESTORE) {
            restoreRelations(m, plan.snapshot().relations(), warnings);
        }

        boolean wholeFolder = plan.kind() != ImportKind.UPDATE;
        job.setKind(plan.kind());
        job.setMediaId(m.getId());
        job.setPluginId(plan.pluginId());
        for (String w : warnings) {
            job.setDetail(job.detail().withWarning(w));
        }
        job.markCommitted(new CommitPlan(m.getId(), wholeFolder, wholeFolder ? List.of() : folders, replaced), now);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", plan.kind().name());
        details.put("mediaId", m.getId().toString());
        details.put("chapters", folders);
        if (!replaced.isEmpty()) {
            details.put("replaced", replaced);
        }
        audit.record("IMPORT_COMMITTED", "import_job", jobId, details);
        faults.checkpoint(Step.IN_TRANSACTION);
        return m.getId();
    }

    private Media createNew(ImportPlan plan) {
        MediaFields f = plan.fields();
        MediaType type = metadata.findType(f.type()).orElseThrow();
        Media m = new Media(f.title(), type, f.language());
        m.setSourceUrl(plan.sourceUrl());
        m.setPluginId(plan.pluginId());
        metadata.apply(m, f, MediaFields.FIELD_NAMES);
        m.setLockedFields(plan.lockFields());
        m.setCover(plan.cover().target(), plan.cover().crop());
        MediaInvariants.requireAuthorOrArtist(m);
        return media.save(m);
    }

    private Media restore(ImportPlan plan, List<String> warnings) {
        MediaFields f = plan.fields();
        MediaType type = metadata.findType(f.type()).orElseThrow();
        Media m = new Media(plan.targetMediaId(), f.title(), type, f.language());
        m.restoreCreatedAt(plan.snapshot().createdAt());
        m.setSourceUrl(plan.sourceUrl());
        m.setPluginId(plan.pluginId());
        metadata.apply(m, f, MediaFields.FIELD_NAMES);
        m.setLockedFields(plan.lockFields());
        m.restoreCover(plan.cover().target(), plan.cover().crop());
        MediaInvariants.requireAuthorOrArtist(m);
        if (plan.snapshot().cover() != null && !plan.cover().target().equals(plan.snapshot().cover().file())) {
            warnings.add("Cover replaced by the first page.");
        }
        return media.save(m);
    }

    /** Relations stored on this media (it is {@code from}); targets not in the library are skipped. */
    void restoreRelations(Media from, List<SnapshotRelation> wanted, List<String> warnings) {
        for (SnapshotRelation r : wanted) {
            if (r.mediaId().equals(from.getId())) {
                continue;
            }
            Media to = media.findById(r.mediaId()).orElse(null);
            if (to == null) {
                warnings.add("Relation " + r.type() + " to " + r.mediaId() + " skipped: that media is not in the library.");
                continue;
            }
            boolean exists = relations.findInvolving(from.getId()).stream()
                    .anyMatch(x -> x.getFrom().getId().equals(from.getId()) && x.getTo().getId().equals(to.getId()));
            if (!exists) {
                relations.save(new MediaRelation(from, to, r.type()));
            }
        }
    }

    /** Replace keeps progress rows; positions past the new last page move to its start. */
    private void clampProgress(UUID chapterId, int pageCount) {
        jdbc.sql("""
                update chapter_progress set page_index = :last, page_offset = 0, updated_at = now()
                where chapter_id = :chapterId and page_index > :last
                """)
                .param("last", pageCount - 1)
                .param("chapterId", chapterId)
                .update();
    }
}

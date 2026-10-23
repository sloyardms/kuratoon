package dev.kuratoon.ingestion;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.ingestion.ImportAdminDtos.Action;
import dev.kuratoon.ingestion.ImportAdminDtos.ConflictRequest;
import dev.kuratoon.ingestion.ImportAdminDtos.DuplicateRequest;
import dev.kuratoon.ingestion.ImportAdminDtos.ImportJobDetail;
import dev.kuratoon.ingestion.ImportAdminDtos.ImportJobSummary;
import dev.kuratoon.ingestion.JobDetail.Attention;
import dev.kuratoon.ingestion.JobDetail.Conflict;
import dev.kuratoon.ingestion.JobDetail.DuplicateChoice;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaFields;
import dev.kuratoon.media.MediaInvariants;
import dev.kuratoon.media.MediaMetadataWriter;
import dev.kuratoon.media.MediaRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Admin actions on import jobs. Each one is audited and re-queues the job where it applies. */
@Service
public class ImportAdminService {

    private static final String TARGET = "import_job";
    private static final Set<Attention> METADATA_REASONS =
            EnumSet.of(Attention.NO_PLUGIN, Attention.NO_METADATA_SOURCE, Attention.INCOMPLETE_METADATA);

    private final ImportJobRepository jobs;
    private final MediaRepository media;
    private final MediaMetadataWriter metadata;
    private final LibraryPaths paths;
    private final AuditService audit;
    private final ImportScheduler scheduler;
    private final Clock clock;

    public ImportAdminService(
            ImportJobRepository jobs,
            MediaRepository media,
            MediaMetadataWriter metadata,
            LibraryPaths paths,
            AuditService audit,
            ImportScheduler scheduler,
            Clock clock) {
        this.jobs = jobs;
        this.media = media;
        this.metadata = metadata;
        this.paths = paths;
        this.audit = audit;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OffsetPage<ImportJobSummary> list(Collection<ImportStatus> statuses, PageParams paging) {
        Page<ImportJob> page = jobs.search(
                statuses == null || statuses.isEmpty() ? null : statuses,
                paging.toPageRequest(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<UUID, String> titles = titles(page.getContent());
        return OffsetPage.of(page, j -> summary(j, titles.get(j.getMediaId())));
    }

    @Transactional(readOnly = true)
    public ImportJobDetail get(UUID id) {
        return detail(find(id));
    }

    @Transactional
    public ImportJobDetail retry(UUID id) {
        ImportJob job = waiting(find(id));
        requeue(job);
        audit.record("IMPORT_RETRIED", TARGET, id, Map.of("folder", job.getSourceFolder()));
        return detail(job);
    }

    /** Stores the admin's metadata (all of it locked once imported) and re-queues the job. */
    @Transactional
    public ImportJobDetail fillMetadata(UUID id, MediaFields fields) {
        ImportJob job = waiting(find(id));
        if (!actions(job).contains(Action.FILL_METADATA)) {
            throw new ConflictException("Metadata can only be filled in for a new media that is missing it.");
        }
        validate(fields);
        job.setDetail(job.detail().withDecisions(job.detail().decisions().withManualMetadata(fields)));
        requeue(job);
        audit.record("IMPORT_METADATA_ENTERED", TARGET, id, Map.of("title", fields.title(), "type", fields.type()));
        return detail(job);
    }

    @Transactional
    public ImportJobDetail resolveConflicts(UUID id, ConflictRequest request) {
        ImportJob job = waiting(find(id));
        if (job.detail().attention() != Attention.CONFLICT) {
            throw new ConflictException("This job has no chapter conflicts to resolve.");
        }
        Set<String> folders = job.detail().conflicts().stream().map(Conflict::folder).collect(Collectors.toSet());
        for (String folder : request.choices().keySet()) {
            if (!folders.contains(folder)) {
                throw new BadRequestException("Chapter " + folder + " is not in conflict.");
            }
        }
        if (!request.choices().keySet().containsAll(folders)) {
            throw new BadRequestException("Choose Skip or Replace for every conflicting chapter.");
        }
        job.setDetail(job.detail().withDecisions(job.detail().decisions().withConflicts(request.choices())));
        requeue(job);
        Map<String, Object> choices = new LinkedHashMap<>();
        request.choices().forEach((k, v) -> choices.put(k, v.name()));
        audit.record("IMPORT_CONFLICT_RESOLVED", TARGET, id, choices);
        return detail(job);
    }

    @Transactional
    public ImportJobDetail resolveDuplicate(UUID id, DuplicateRequest request) {
        ImportJob job = waiting(find(id));
        if (job.detail().attention() != Attention.DUPLICATE || job.detail().duplicate() == null) {
            throw new ConflictException("This job has no duplicate to resolve.");
        }
        UUID target = request.choice() == DuplicateChoice.UPDATE_EXISTING ? job.detail().duplicate().mediaId() : null;
        job.setDetail(job.detail().withDecisions(job.detail().decisions().withDuplicate(request.choice(), target)));
        requeue(job);
        audit.record("IMPORT_DUPLICATE_RESOLVED", TARGET, id, Map.of("choice", request.choice().name()));
        return detail(job);
    }

    /**
     * Finishes the job and renames its source folder to {@code .dismissed-<name>-<jobId>}, so the
     * poller ignores it. A job whose import was already saved cannot be dismissed (Retry finishes it).
     */
    @Transactional
    public ImportJobDetail dismiss(UUID id) {
        ImportJob job = waiting(find(id));
        if (job.getCommittedAt() != null) {
            throw new ConflictException("This import was already saved to the library; press Retry to finish it.");
        }
        Path source = paths.source(job.getSourceFolder());
        Path target = paths.dismissed(job.getSourceFolder(), job.getId());
        if (Files.exists(source)) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new ConflictException("Could not rename the import folder: " + e.getMessage());
            }
            // Keep the folder where it was if the transaction rolls back.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            Files.move(target, source, StandardCopyOption.ATOMIC_MOVE);
                        } catch (IOException ignored) {
                            // The admin can rename it back by hand.
                        }
                    }
                }
            });
        }
        job.moveTo(ImportStatus.DISMISSED, "Dismissed. The folder was renamed to " + target.getFileName() + ".",
                clock.instant());
        audit.record("IMPORT_DISMISSED", TARGET, id, Map.of("folder", job.getSourceFolder()));
        return detail(job);
    }

    // --- helpers --------------------------------------------------------------------------------

    private void validate(MediaFields fields) {
        if (fields.title() == null) {
            throw new BadRequestException("A title is required.");
        }
        if (fields.type() == null || metadata.findType(fields.type()).isEmpty()) {
            throw new BadRequestException("Choose a known media type.");
        }
        if (fields.authors().isEmpty() && fields.artists().isEmpty()) {
            throw new BadRequestException(MediaInvariants.AUTHOR_OR_ARTIST_REQUIRED);
        }
        if (fields.year() != null && (fields.year() < 1000 || fields.year() > 9999)) {
            throw new BadRequestException("The year must have four digits.");
        }
    }

    private void requeue(ImportJob job) {
        job.moveTo(ImportStatus.QUEUED, null, clock.instant());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scheduler.trigger();
            }
        });
    }

    private ImportJob find(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Import job not found."));
    }

    private static ImportJob waiting(ImportJob job) {
        if (!job.getStatus().waitingForAdmin()) {
            throw new ConflictException("The job is " + job.getStatus() + "; only jobs that need attention or failed"
                    + " can be changed.");
        }
        return job;
    }

    static List<Action> actions(ImportJob job) {
        List<Action> actions = new ArrayList<>();
        if (!job.getStatus().waitingForAdmin()) {
            return actions;
        }
        actions.add(Action.RETRY);
        Attention attention = job.detail().attention();
        boolean committed = job.getCommittedAt() != null;
        if (!committed && METADATA_REASONS.contains(attention) && job.getKind() != ImportKind.UPDATE
                && job.getKind() != ImportKind.RESTORE) {
            actions.add(Action.FILL_METADATA);
        }
        if (attention == Attention.CONFLICT) {
            actions.add(Action.RESOLVE_CONFLICT);
        }
        if (attention == Attention.DUPLICATE) {
            actions.add(Action.RESOLVE_DUPLICATE);
        }
        if (!committed) {
            actions.add(Action.DISMISS);
        }
        return actions;
    }

    private Map<UUID, String> titles(List<ImportJob> page) {
        List<UUID> ids = page.stream().map(ImportJob::getMediaId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> titles = new HashMap<>();
        media.findAllById(ids).forEach(m -> titles.put(m.getId(), m.getTitle()));
        return titles;
    }

    private ImportJobSummary summary(ImportJob j, String mediaTitle) {
        return new ImportJobSummary(j.getId(), j.getSourceFolder(), j.getKind(), j.getStatus(), j.getMessage(),
                j.detail().attention(), j.getMediaId(), mediaTitle, j.detail().chapters().size(), j.getAttempts(),
                j.getCreatedAt(), j.getUpdatedAt(), j.getFinishedAt(), actions(j));
    }

    private ImportJobDetail detail(ImportJob j) {
        String title = j.getMediaId() == null ? null : media.findById(j.getMediaId()).map(Media::getTitle).orElse(null);
        JobDetail d = j.detail();
        return new ImportJobDetail(summary(j, title), d.url(), d.chapters(), d.warnings(), d.missing(), d.prefill(),
                d.conflicts(), d.duplicate(), d.decisions(), j.getCommittedAt() != null);
    }
}

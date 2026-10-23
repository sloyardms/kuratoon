package dev.kuratoon.ingestion;

import dev.kuratoon.ingestion.JobDetail.Attention;
import dev.kuratoon.ingestion.JobDetail.ChapterRow;
import dev.kuratoon.ingestion.JobDetail.Conflict;
import dev.kuratoon.ingestion.JobDetail.ConflictChoice;
import dev.kuratoon.ingestion.JobDetail.Decisions;
import dev.kuratoon.ingestion.JobDetail.Duplicate;
import dev.kuratoon.ingestion.JobDetail.DuplicateChoice;
import dev.kuratoon.media.MediaFields;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Imports admin page API (docs/spec/02-ingestion.md "Job lifecycle"). */
public final class ImportAdminDtos {

    private ImportAdminDtos() {}

    /** What the admin can do with a job right now. Choose plugin arrives with plugins (phase 4). */
    public enum Action {
        RETRY,
        FILL_METADATA,
        RESOLVE_CONFLICT,
        RESOLVE_DUPLICATE,
        DISMISS
    }

    public record ImportJobSummary(
            UUID id,
            String sourceFolder,
            ImportKind kind,
            ImportStatus status,
            String message,
            Attention attention,
            UUID mediaId,
            String mediaTitle,
            int chapterCount,
            int attempts,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt,
            List<Action> actions) {}

    public record ImportJobDetail(
            ImportJobSummary job,
            String url,
            List<ChapterRow> chapters,
            List<String> warnings,
            List<String> missing,
            MediaFields prefill,
            List<Conflict> conflicts,
            Duplicate duplicate,
            Decisions decisions,
            boolean committed) {}

    /** @param choices per conflicting chapter folder */
    public record ConflictRequest(@NotEmpty Map<String, ConflictChoice> choices) {}

    public record DuplicateRequest(@NotNull DuplicateChoice choice) {}
}

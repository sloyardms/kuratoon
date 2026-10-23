package dev.kuratoon.ingestion;

import dev.kuratoon.media.MediaFields;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code import_job.detail}: what the Imports page shows and what the admin decided. Immutable;
 * replace it with the {@code with*} methods.
 *
 * @param attention why the job needs attention (null otherwise)
 * @param url normalized metadata URL, when known
 * @param chapters the parsed chapter table
 * @param missing metadata fields needed to create the media
 * @param prefill values for the manual metadata form (from the manifest)
 * @param conflicts chapters that already exist in the target media (UPDATE)
 * @param duplicate an existing media with the same title and type (NEW)
 * @param decisions admin choices; kept on Retry
 * @param commit what the commit step wrote, so a crashed job can be rolled forward
 */
public record JobDetail(
        Attention attention,
        String url,
        List<ChapterRow> chapters,
        List<String> warnings,
        List<String> missing,
        MediaFields prefill,
        List<Conflict> conflicts,
        Duplicate duplicate,
        Decisions decisions,
        CommitPlan commit) {

    public static final JobDetail EMPTY =
            new JobDetail(null, null, List.of(), List.of(), List.of(), null, List.of(), null, Decisions.NONE, null);

    public JobDetail {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        missing = missing == null ? List.of() : List.copyOf(missing);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        decisions = decisions == null ? Decisions.NONE : decisions;
    }

    /** Reason codes for NEEDS_ATTENTION; the UI picks the actions from them. */
    public enum Attention {
        /** kuratoon.yml / source.txt unreadable. */
        INVALID_MANIFEST,
        /** Chapter table problems: no number, duplicate number, no pages, nested folders. */
        CHAPTERS,
        /** An image could not be read. */
        UNREADABLE_IMAGE,
        /** No URL, no HTML, no snapshot and no complete manifest. */
        NO_METADATA_SOURCE,
        /** A URL or saved page exists but no plugin can provide metadata. */
        NO_PLUGIN,
        /** Metadata given but not enough to create the media (e.g. unknown type, no creators). */
        INCOMPLETE_METADATA,
        /** Same title and type as an existing media. */
        DUPLICATE,
        /** UPDATE with chapter numbers that already exist. */
        CONFLICT,
        /** RESTORE of a media id that exists. */
        ALREADY_IN_LIBRARY,
        /** RESTORE whose url is another media's source_url. */
        URL_IN_USE,
        /** Startup recovery could not finish a committed job. */
        RECOVERY
    }

    /**
     * @param sourceFolder folder inside the import folder ("" for a one-shot)
     * @param number null when no number was found
     * @param folder canonical library folder
     */
    public record ChapterRow(
            String sourceFolder, BigDecimal number, String title, String folder, int pageCount, List<String> warnings) {

        public ChapterRow {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record Conflict(BigDecimal number, String folder, UUID existingChapterId, int existingPageCount, int newPageCount) {}

    public record Duplicate(UUID mediaId, String title) {}

    public enum DuplicateChoice {
        UPDATE_EXISTING,
        CREATE_NEW
    }

    public enum ConflictChoice {
        SKIP,
        REPLACE
    }

    /**
     * @param duplicateTarget the media to update when {@code duplicate} is UPDATE_EXISTING
     * @param conflicts per canonical chapter folder
     * @param manualMetadata the admin's metadata form; all of its fields become locked
     */
    public record Decisions(
            DuplicateChoice duplicate,
            UUID duplicateTarget,
            Map<String, ConflictChoice> conflicts,
            MediaFields manualMetadata) {

        public static final Decisions NONE = new Decisions(null, null, Map.of(), null);

        public Decisions {
            conflicts = conflicts == null ? Map.of() : Map.copyOf(conflicts);
        }

        public Decisions withDuplicate(DuplicateChoice choice, UUID target) {
            return new Decisions(choice, target, conflicts, manualMetadata);
        }

        public Decisions withConflicts(Map<String, ConflictChoice> choices) {
            Map<String, ConflictChoice> merged = new LinkedHashMap<>(conflicts);
            merged.putAll(choices);
            return new Decisions(duplicate, duplicateTarget, merged, manualMetadata);
        }

        public Decisions withManualMetadata(MediaFields metadata) {
            return new Decisions(duplicate, duplicateTarget, conflicts, metadata);
        }
    }

    /**
     * Written in the commit transaction (spec 02 step 3).
     *
     * @param mediaFolderNew the staging dir holds the whole media folder (NEW, RESTORE)
     * @param folders chapter folders to move into place (UPDATE)
     * @param replaced of those, the ones that replace an existing chapter folder
     */
    public record CommitPlan(UUID mediaId, boolean mediaFolderNew, List<String> folders, List<String> replaced) {

        public CommitPlan {
            folders = folders == null ? List.of() : List.copyOf(folders);
            replaced = replaced == null ? List.of() : List.copyOf(replaced);
        }
    }

    // --- copies -----------------------------------------------------------------------------------

    /** The result of a fresh resolution: decisions survive, everything else is replaced. */
    public JobDetail resolved(
            Attention attention,
            String url,
            List<ChapterRow> chapters,
            List<String> warnings,
            List<String> missing,
            MediaFields prefill,
            List<Conflict> conflicts,
            Duplicate duplicate) {
        return new JobDetail(attention, url, chapters, warnings, missing, prefill, conflicts, duplicate, decisions, null);
    }

    public JobDetail withDecisions(Decisions newDecisions) {
        return new JobDetail(attention, url, chapters, warnings, missing, prefill, conflicts, duplicate, newDecisions, commit);
    }

    public JobDetail withCommit(CommitPlan plan) {
        return new JobDetail(attention, url, chapters, warnings, missing, prefill, conflicts, duplicate, decisions, plan);
    }

    public JobDetail withAttention(Attention reason) {
        return new JobDetail(reason, url, chapters, warnings, missing, prefill, conflicts, duplicate, decisions, commit);
    }

    public JobDetail withWarning(String warning) {
        List<String> all = new ArrayList<>(warnings);
        all.add(warning);
        return new JobDetail(attention, url, chapters, all, missing, prefill, conflicts, duplicate, decisions, commit);
    }
}

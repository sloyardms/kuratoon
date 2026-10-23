package dev.kuratoon.ingestion;

import dev.kuratoon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One import folder being processed (docs/spec/02-ingestion.md "Job lifecycle"). */
@Entity
@Table(name = "import_job")
public class ImportJob extends BaseEntity {

    @Column(name = "source_folder", nullable = false, updatable = false)
    private String sourceFolder;

    @Enumerated(EnumType.STRING)
    private ImportKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    /** Target media once known. A plain id: the job outlives a deleted media (FK SET NULL). */
    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "plugin_id")
    private String pluginId;

    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    private JobDetail detail;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    protected ImportJob() {}

    public ImportJob(String sourceFolder, Instant now) {
        this.sourceFolder = Objects.requireNonNull(sourceFolder);
        this.status = ImportStatus.QUEUED;
        this.detail = JobDetail.EMPTY;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** RESOLVING → … : a new attempt starts from the source folder. */
    public void startAttempt(Instant now) {
        status = ImportStatus.RESOLVING;
        attempts++;
        message = null;
        committedAt = null;
        detail = detail().withCommit(null);
        updatedAt = now;
    }

    public void moveTo(ImportStatus next, String newMessage, Instant now) {
        this.status = Objects.requireNonNull(next);
        this.message = newMessage;
        this.updatedAt = now;
        this.finishedAt = next.finished() ? now : null;
    }

    public void setKind(ImportKind kind) {
        this.kind = kind;
    }

    public void setMediaId(UUID mediaId) {
        this.mediaId = mediaId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public void setDetail(JobDetail detail) {
        this.detail = Objects.requireNonNull(detail);
    }

    /** Commit step 3: set in the same transaction as the library rows. */
    public void markCommitted(JobDetail.CommitPlan plan, Instant now) {
        this.committedAt = now;
        this.detail = detail().withCommit(plan);
        this.updatedAt = now;
    }

    public String getSourceFolder() {
        return sourceFolder;
    }

    public ImportKind getKind() {
        return kind;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getMessage() {
        return message;
    }

    public JobDetail detail() {
        return detail == null ? JobDetail.EMPTY : detail;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }
}

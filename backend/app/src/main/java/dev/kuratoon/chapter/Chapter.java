package dev.kuratoon.chapter;

import dev.kuratoon.common.BaseEntity;
import dev.kuratoon.media.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An ordered unit of a media, identified by a decimal number. Pages are stored inline (ADR 0010). */
@Entity
@Table(name = "chapter")
public class Chapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, updatable = false)
    private Media media;

    @Column(nullable = false, precision = 9, scale = 3)
    private BigDecimal number;

    private String title;

    @Column(nullable = false)
    private String folder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<PageEntry> pages;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Chapter() {}

    /**
     * Creates the chapter. The media's denormalized chapter counters are not touched here: callers
     * run {@link ChapterCounters#recalculate} in the same transaction.
     *
     * @param createdAt "chapter added" date; the import time, or the sidecar value on restore
     */
    public Chapter(Media media, BigDecimal number, String title, String folder, List<PageEntry> pages, Instant createdAt) {
        this.media = Objects.requireNonNull(media);
        this.number = Objects.requireNonNull(number);
        this.title = title;
        this.folder = Objects.requireNonNull(folder);
        setPages(pages);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    /**
     * Swaps the pages (UPDATE "Replace"). The chapter keeps its id, so users' progress rows stay;
     * it also keeps {@code created_at}, since it is not a newly added chapter.
     */
    public void replacePages(List<PageEntry> pages) {
        setPages(pages);
        this.updatedAt = Instant.now();
    }

    private void setPages(List<PageEntry> pages) {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("a chapter needs at least one page");
        }
        this.pages = List.copyOf(pages);
        this.pageCount = this.pages.size();
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Media getMedia() {
        return media;
    }

    public BigDecimal getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getFolder() {
        return folder;
    }

    public List<PageEntry> getPages() {
        return pages;
    }

    public int getPageCount() {
        return pageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

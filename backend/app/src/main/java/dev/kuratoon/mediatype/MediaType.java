package dev.kuratoon.mediatype;

import dev.kuratoon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Admin-defined category (Manga, Manhwa, …). The slug is what manifests reference. */
@Entity
@Table(name = "media_type")
public class MediaType extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MediaType() {}

    public MediaType(String name, String slug, String color, int sortOrder) {
        update(name, slug, color, sortOrder);
        this.createdAt = Instant.now();
    }

    public void update(String name, String slug, String color, int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

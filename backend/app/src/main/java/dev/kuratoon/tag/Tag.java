package dev.kuratoon.tag;

import dev.kuratoon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tag")
public class Tag extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {}

    public Tag(String name, String slug, TagKind kind) {
        update(name, slug, kind);
        this.createdAt = Instant.now();
    }

    public void update(String name, String slug, TagKind kind) {
        this.name = name;
        this.slug = slug;
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public TagKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

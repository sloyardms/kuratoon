package dev.kuratoon.media;

import dev.kuratoon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code to} is a {@link #getType() type} of {@code from}. Stored in one direction only. */
@Entity
@Table(name = "media_relation")
public class MediaRelation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_media_id", nullable = false, updatable = false)
    private Media from;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_media_id", nullable = false, updatable = false)
    private Media to;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MediaRelation() {}

    public MediaRelation(Media from, Media to, RelationType type) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public Media getFrom() {
        return from;
    }

    public Media getTo() {
        return to;
    }

    public RelationType getType() {
        return type;
    }
}

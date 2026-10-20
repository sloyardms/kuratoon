package dev.kuratoon.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Entity with an application-assigned UUIDv7 id (ADR 0004). Implements {@link Persistable} so
 * {@code save()} of a new entity persists directly instead of selecting and merging.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected BaseEntity() {
        this(UuidV7.generate());
    }

    /** For rows re-created with a known id (restore from sidecar). */
    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof BaseEntity other
                && org.hibernate.Hibernate.getClass(this) == org.hibernate.Hibernate.getClass(other)
                && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}

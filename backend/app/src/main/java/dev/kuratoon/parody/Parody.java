package dev.kuratoon.parody;

import dev.kuratoon.common.BaseEntity;
import dev.kuratoon.common.Names;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** The source work a doujinshi parodies. Matched by normalized name. */
@Entity
@Table(name = "parody")
public class Parody extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Parody() {}

    public Parody(String name) {
        rename(name);
        this.createdAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name.strip();
        this.normalizedName = Names.normalize(name);
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

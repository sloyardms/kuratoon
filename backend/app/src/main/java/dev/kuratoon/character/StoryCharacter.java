package dev.kuratoon.character;

import dev.kuratoon.common.BaseEntity;
import dev.kuratoon.common.Names;
import dev.kuratoon.parody.Parody;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A character, optionally tied to a parody (table {@code character}; named StoryCharacter to avoid
 * clashing with {@link java.lang.Character}). Unique by normalized name + parody.
 */
@Entity
@Table(name = "character")
public class StoryCharacter extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parody_id")
    private Parody parody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoryCharacter() {}

    public StoryCharacter(String name, Parody parody) {
        update(name, parody);
        this.createdAt = Instant.now();
    }

    public void update(String name, Parody parody) {
        this.name = name.strip();
        this.normalizedName = Names.normalize(name);
        this.parody = parody;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    /** May be null. */
    public Parody getParody() {
        return parody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

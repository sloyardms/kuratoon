package dev.kuratoon.media;

import dev.kuratoon.creator.Creator;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.util.Objects;

/** A creator credited on a media with one role. The same creator may hold both roles. */
@Entity
@Table(name = "media_creator")
public class MediaCreator {

    @EmbeddedId
    private MediaCreatorId id;

    @MapsId("mediaId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @MapsId("creatorId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    protected MediaCreator() {}

    MediaCreator(Media media, Creator creator, CreatorRole role) {
        this.id = new MediaCreatorId(media.getId(), creator.getId(), role);
        this.media = media;
        this.creator = creator;
    }

    public Creator getCreator() {
        return creator;
    }

    public CreatorRole getRole() {
        return id.role();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MediaCreator other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

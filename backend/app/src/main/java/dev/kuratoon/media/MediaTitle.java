package dev.kuratoon.media;

import dev.kuratoon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Alternative title of a media. */
@Entity
@Table(name = "media_title")
public class MediaTitle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, updatable = false)
    private Media media;

    @Column(nullable = false)
    private String title;

    private String language;

    protected MediaTitle() {}

    MediaTitle(Media media, String title, String language) {
        this.media = media;
        this.title = title;
        this.language = language;
    }

    public String getTitle() {
        return title;
    }

    /** BCP-47, may be null. */
    public String getLanguage() {
        return language;
    }
}

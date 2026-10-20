package dev.kuratoon.media;

import dev.kuratoon.character.StoryCharacter;
import dev.kuratoon.common.BaseEntity;
import dev.kuratoon.creator.Creator;
import dev.kuratoon.mediatype.MediaType;
import dev.kuratoon.parody.Parody;
import dev.kuratoon.tag.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A series or one-shot. The id is also the library folder name. Counters marked denorm in
 * docs/spec/01-data-model.md are maintained in the same transaction as the change affecting them.
 *
 * <p>Invariant enforced by the media write services (phase 3, ingestion and media editing): at
 * least one creator with role AUTHOR or ARTIST.
 */
@Entity
@Table(name = "media")
public class Media extends BaseEntity {

    @Column(nullable = false)
    private String title;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_type_id", nullable = false)
    private MediaType mediaType;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false)
    private PublicationStatus publicationStatus = PublicationStatus.UNKNOWN;

    @Column(name = "publication_year")
    private Short publicationYear;

    @Column(name = "is_adult", nullable = false)
    private boolean adult;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "plugin_id")
    private String pluginId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "locked_fields", nullable = false)
    private List<String> lockedFields = new ArrayList<>();

    @Column(name = "cover_file")
    private String coverFile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cover_crop")
    private CoverCrop coverCrop;

    @Column(name = "cover_version", nullable = false)
    private int coverVersion;

    @Column(name = "chapter_count", nullable = false)
    private int chapterCount;

    @Column(name = "latest_chapter_added_at")
    private Instant latestChapterAddedAt;

    @Column(name = "avg_rating", precision = 4, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "favorite_count", nullable = false)
    private int favoriteCount;

    @Column(name = "metadata_refreshed_at")
    private Instant metadataRefreshedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "media", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MediaTitle> alternativeTitles = new ArrayList<>();

    @OneToMany(mappedBy = "media", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MediaCreator> creators = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "media_tag",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "media_parody",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "parody_id"))
    private Set<Parody> parodies = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "media_character",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id"))
    private Set<StoryCharacter> characters = new LinkedHashSet<>();

    protected Media() {}

    public Media(String title, MediaType mediaType, String language) {
        this.title = Objects.requireNonNull(title);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.language = Objects.requireNonNull(language);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** For a media re-created from a sidecar snapshot with its original id. */
    public Media(UUID id, String title, MediaType mediaType, String language) {
        super(id);
        this.title = Objects.requireNonNull(title);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.language = Objects.requireNonNull(language);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void addAlternativeTitle(String altTitle, String titleLanguage) {
        alternativeTitles.add(new MediaTitle(this, altTitle, titleLanguage));
    }

    public void addCreator(Creator creator, CreatorRole role) {
        creators.add(new MediaCreator(this, creator, role));
    }

    public void addTag(Tag tag) {
        tags.add(tag);
    }

    public void addParody(Parody parody) {
        parodies.add(parody);
    }

    public void addCharacter(StoryCharacter character) {
        characters.add(character);
    }

    /** Keeps the denormalized chapter counters in step when a chapter is added. */
    public void chapterAdded(Instant addedAt) {
        chapterCount++;
        if (latestChapterAddedAt == null || addedAt.isAfter(latestChapterAddedAt)) {
            latestChapterAddedAt = addedAt;
        }
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPublicationStatus(PublicationStatus publicationStatus) {
        this.publicationStatus = Objects.requireNonNull(publicationStatus);
    }

    public void setPublicationYear(Short publicationYear) {
        this.publicationYear = publicationYear;
    }

    public void setAdult(boolean adult) {
        this.adult = adult;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getLanguage() {
        return language;
    }

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public Short getPublicationYear() {
        return publicationYear;
    }

    public boolean isAdult() {
        return adult;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPluginId() {
        return pluginId;
    }

    public List<String> getLockedFields() {
        return Collections.unmodifiableList(lockedFields);
    }

    public String getCoverFile() {
        return coverFile;
    }

    public CoverCrop getCoverCrop() {
        return coverCrop;
    }

    public int getCoverVersion() {
        return coverVersion;
    }

    public int getChapterCount() {
        return chapterCount;
    }

    public Instant getLatestChapterAddedAt() {
        return latestChapterAddedAt;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public int getFavoriteCount() {
        return favoriteCount;
    }

    public Instant getMetadataRefreshedAt() {
        return metadataRefreshedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<MediaTitle> getAlternativeTitles() {
        return Collections.unmodifiableList(alternativeTitles);
    }

    public Set<MediaCreator> getCreators() {
        return Collections.unmodifiableSet(creators);
    }

    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public Set<Parody> getParodies() {
        return Collections.unmodifiableSet(parodies);
    }

    public Set<StoryCharacter> getCharacters() {
        return Collections.unmodifiableSet(characters);
    }
}

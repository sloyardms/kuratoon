package dev.kuratoon.media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record MediaCreatorId(
        @Column(name = "media_id") UUID mediaId,
        @Column(name = "creator_id") UUID creatorId,
        @Enumerated(EnumType.STRING) @Column(name = "role") CreatorRole role)
        implements Serializable {}

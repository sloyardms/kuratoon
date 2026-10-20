package dev.kuratoon.media;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    boolean existsByMediaTypeId(UUID mediaTypeId);
}

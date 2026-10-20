package dev.kuratoon.mediatype;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.common.Slugs;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.mediatype.MediaTypeDtos.MediaTypeRequest;
import dev.kuratoon.mediatype.MediaTypeDtos.MediaTypeResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaTypeService {

    private static final String TARGET = "media_type";

    private final MediaTypeRepository types;
    private final MediaRepository media;
    private final AuditService audit;

    public MediaTypeService(MediaTypeRepository types, MediaRepository media, AuditService audit) {
        this.types = types;
        this.media = media;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OffsetPage<MediaTypeResponse> list(String q, PageParams paging) {
        Sort sort = Sort.by("sortOrder", "name", "id");
        return OffsetPage.of(types.search(Queries.containsPattern(q), paging.toPageRequest(sort)),
                MediaTypeResponse::of);
    }

    @Transactional(readOnly = true)
    public MediaTypeResponse get(UUID id) {
        return MediaTypeResponse.of(find(id));
    }

    @Transactional
    public MediaTypeResponse create(MediaTypeRequest request) {
        String name = request.name().strip();
        String slug = Slugs.resolve(request.slug(), name);
        if (types.existsByName(name)) {
            throw new ConflictException("A media type named '" + name + "' already exists.");
        }
        if (types.existsBySlug(slug)) {
            throw new ConflictException("A media type with slug '" + slug + "' already exists.");
        }
        MediaType type = types.save(new MediaType(name, slug, request.color(), sortOrder(request)));
        audit.record("MEDIA_TYPE_CREATED", TARGET, type.getId(), Map.of("name", name, "slug", slug));
        return MediaTypeResponse.of(type);
    }

    @Transactional
    public MediaTypeResponse update(UUID id, MediaTypeRequest request) {
        MediaType type = find(id);
        String name = request.name().strip();
        String slug = Slugs.resolve(request.slug(), name);
        if (types.existsByNameAndIdNot(name, id)) {
            throw new ConflictException("A media type named '" + name + "' already exists.");
        }
        if (types.existsBySlugAndIdNot(slug, id)) {
            throw new ConflictException("A media type with slug '" + slug + "' already exists.");
        }
        type.update(name, slug, request.color(), sortOrder(request));
        audit.record("MEDIA_TYPE_UPDATED", TARGET, id, Map.of("name", name, "slug", slug));
        return MediaTypeResponse.of(type);
    }

    @Transactional
    public void delete(UUID id) {
        MediaType type = find(id);
        if (media.existsByMediaTypeId(id)) {
            throw new ConflictException(
                    "Media type '" + type.getName() + "' is in use; reassign its media to another type first.");
        }
        types.delete(type);
        audit.record("MEDIA_TYPE_DELETED", TARGET, id, Map.of("name", type.getName(), "slug", type.getSlug()));
    }

    private MediaType find(UUID id) {
        return types.findById(id).orElseThrow(() -> NotFoundException.of("Media type", id));
    }

    private static int sortOrder(MediaTypeRequest request) {
        return request.sortOrder() == null ? 0 : request.sortOrder();
    }
}

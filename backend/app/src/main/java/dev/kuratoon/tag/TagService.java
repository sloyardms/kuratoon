package dev.kuratoon.tag;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.common.Slugs;
import dev.kuratoon.tag.TagDtos.TagRequest;
import dev.kuratoon.tag.TagDtos.TagResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    private static final String TARGET = "tag";

    private final TagRepository tags;
    private final AuditService audit;

    public TagService(TagRepository tags, AuditService audit) {
        this.tags = tags;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OffsetPage<TagResponse> list(TagKind kind, String q, PageParams paging) {
        Sort sort = Sort.by("kind", "name", "id");
        return OffsetPage.of(tags.search(kind, Queries.containsPattern(q), paging.toPageRequest(sort)),
                TagResponse::of);
    }

    @Transactional(readOnly = true)
    public TagResponse get(UUID id) {
        return TagResponse.of(find(id));
    }

    @Transactional
    public TagResponse create(TagRequest request) {
        String name = request.name().strip();
        String slug = Slugs.resolve(request.slug(), name);
        if (tags.existsByKindAndSlug(request.kind(), slug)) {
            throw duplicate(request.kind(), slug);
        }
        Tag tag = tags.save(new Tag(name, slug, request.kind()));
        audit.record("TAG_CREATED", TARGET, tag.getId(), details(tag));
        return TagResponse.of(tag);
    }

    @Transactional
    public TagResponse update(UUID id, TagRequest request) {
        Tag tag = find(id);
        String name = request.name().strip();
        String slug = Slugs.resolve(request.slug(), name);
        if (tags.existsByKindAndSlugAndIdNot(request.kind(), slug, id)) {
            throw duplicate(request.kind(), slug);
        }
        tag.update(name, slug, request.kind());
        audit.record("TAG_UPDATED", TARGET, id, details(tag));
        return TagResponse.of(tag);
    }

    /** Also removes the tag from all media and user exclusions (FKs cascade). */
    @Transactional
    public void delete(UUID id) {
        Tag tag = find(id);
        tags.delete(tag);
        audit.record("TAG_DELETED", TARGET, id, details(tag));
    }

    private Tag find(UUID id) {
        return tags.findById(id).orElseThrow(() -> NotFoundException.of("Tag", id));
    }

    private static ConflictException duplicate(TagKind kind, String slug) {
        return new ConflictException("A " + kind + " tag with slug '" + slug + "' already exists.");
    }

    private static Map<String, Object> details(Tag tag) {
        return Map.of("name", tag.getName(), "slug", tag.getSlug(), "kind", tag.getKind());
    }
}

package dev.kuratoon.creator;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.Names;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.creator.CreatorDtos.CreatorRequest;
import dev.kuratoon.creator.CreatorDtos.CreatorResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorService {

    private static final String TARGET = "creator";

    private final CreatorRepository creators;
    private final AuditService audit;

    public CreatorService(CreatorRepository creators, AuditService audit) {
        this.creators = creators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OffsetPage<CreatorResponse> list(String q, PageParams paging) {
        Sort sort = Sort.by("name", "id");
        return OffsetPage.of(creators.search(Queries.containsPattern(q), paging.toPageRequest(sort)),
                CreatorResponse::of);
    }

    @Transactional(readOnly = true)
    public CreatorResponse get(UUID id) {
        return CreatorResponse.of(find(id));
    }

    @Transactional
    public CreatorResponse create(CreatorRequest request) {
        String normalized = Names.normalize(request.name());
        if (creators.existsByNormalizedName(normalized)) {
            throw duplicate(request.name());
        }
        Creator creator = creators.save(new Creator(request.name()));
        audit.record("CREATOR_CREATED", TARGET, creator.getId(), Map.of("name", creator.getName()));
        return CreatorResponse.of(creator);
    }

    @Transactional
    public CreatorResponse update(UUID id, CreatorRequest request) {
        Creator creator = find(id);
        if (creators.existsByNormalizedNameAndIdNot(Names.normalize(request.name()), id)) {
            throw duplicate(request.name());
        }
        String oldName = creator.getName();
        creator.rename(request.name());
        audit.record("CREATOR_UPDATED", TARGET, id, Map.of("from", oldName, "to", creator.getName()));
        return CreatorResponse.of(creator);
    }

    /** A creator credited on any media cannot be deleted (every media needs an author or artist). */
    @Transactional
    public void delete(UUID id) {
        Creator creator = find(id);
        if (creators.isCredited(id)) {
            throw new ConflictException("Creator '" + creator.getName()
                    + "' is credited on media; remove the credits or rename the creator instead.");
        }
        creators.delete(creator);
        audit.record("CREATOR_DELETED", TARGET, id, Map.of("name", creator.getName()));
    }

    private Creator find(UUID id) {
        return creators.findById(id).orElseThrow(() -> NotFoundException.of("Creator", id));
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("A creator named '" + name.strip() + "' already exists.");
    }
}

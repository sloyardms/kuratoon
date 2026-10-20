package dev.kuratoon.parody;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.character.StoryCharacterRepository;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.Names;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.parody.ParodyDtos.ParodyRequest;
import dev.kuratoon.parody.ParodyDtos.ParodyResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParodyService {

    private static final String TARGET = "parody";

    private final ParodyRepository parodies;
    private final StoryCharacterRepository characters;
    private final AuditService audit;

    public ParodyService(ParodyRepository parodies, StoryCharacterRepository characters, AuditService audit) {
        this.parodies = parodies;
        this.characters = characters;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OffsetPage<ParodyResponse> list(String q, PageParams paging) {
        Sort sort = Sort.by("name", "id");
        return OffsetPage.of(parodies.search(Queries.containsPattern(q), paging.toPageRequest(sort)),
                ParodyResponse::of);
    }

    @Transactional(readOnly = true)
    public ParodyResponse get(UUID id) {
        return ParodyResponse.of(find(id));
    }

    @Transactional
    public ParodyResponse create(ParodyRequest request) {
        if (parodies.existsByNormalizedName(Names.normalize(request.name()))) {
            throw duplicate(request.name());
        }
        Parody parody = parodies.save(new Parody(request.name()));
        audit.record("PARODY_CREATED", TARGET, parody.getId(), Map.of("name", parody.getName()));
        return ParodyResponse.of(parody);
    }

    @Transactional
    public ParodyResponse update(UUID id, ParodyRequest request) {
        Parody parody = find(id);
        if (parodies.existsByNormalizedNameAndIdNot(Names.normalize(request.name()), id)) {
            throw duplicate(request.name());
        }
        String oldName = parody.getName();
        parody.rename(request.name());
        audit.record("PARODY_UPDATED", TARGET, id, Map.of("from", oldName, "to", parody.getName()));
        return ParodyResponse.of(parody);
    }

    /**
     * Removes the parody from all media; its characters keep existing without a parody
     * ({@code character.parody_id ON DELETE SET NULL}). Refused when that would create two
     * parody-less characters with the same name.
     */
    @Transactional
    public void delete(UUID id) {
        Parody parody = find(id);
        List<String> colliding = characters.namesCollidingWhenParodyRemoved(id);
        if (!colliding.isEmpty()) {
            throw new ConflictException("Deleting parody '" + parody.getName()
                    + "' would duplicate characters that already exist without a parody: "
                    + String.join(", ", colliding) + ". Merge or rename them first.");
        }
        parodies.delete(parody);
        audit.record("PARODY_DELETED", TARGET, id, Map.of("name", parody.getName()));
    }

    private Parody find(UUID id) {
        return parodies.findById(id).orElseThrow(() -> NotFoundException.of("Parody", id));
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("A parody named '" + name.strip() + "' already exists.");
    }
}

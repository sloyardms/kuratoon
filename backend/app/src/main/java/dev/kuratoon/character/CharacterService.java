package dev.kuratoon.character;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.character.CharacterDtos.CharacterRequest;
import dev.kuratoon.character.CharacterDtos.CharacterResponse;
import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.Names;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.parody.Parody;
import dev.kuratoon.parody.ParodyRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterService {

    private static final String TARGET = "character";

    private final StoryCharacterRepository characters;
    private final ParodyRepository parodies;
    private final AuditService audit;

    public CharacterService(StoryCharacterRepository characters, ParodyRepository parodies, AuditService audit) {
        this.characters = characters;
        this.parodies = parodies;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OffsetPage<CharacterResponse> list(UUID parodyId, String q, PageParams paging) {
        Sort sort = Sort.by("name", "id");
        return OffsetPage.of(
                characters.search(parodyId, Queries.containsPattern(q), paging.toPageRequest(sort)),
                CharacterResponse::of);
    }

    @Transactional(readOnly = true)
    public CharacterResponse get(UUID id) {
        return CharacterResponse.of(find(id));
    }

    @Transactional
    public CharacterResponse create(CharacterRequest request) {
        Parody parody = parody(request.parodyId());
        ensureUnique(request.name(), parody, null);
        StoryCharacter character = characters.save(new StoryCharacter(request.name(), parody));
        audit.record("CHARACTER_CREATED", TARGET, character.getId(), details(character));
        return CharacterResponse.of(character);
    }

    @Transactional
    public CharacterResponse update(UUID id, CharacterRequest request) {
        StoryCharacter character = find(id);
        Parody parody = parody(request.parodyId());
        ensureUnique(request.name(), parody, id);
        character.update(request.name(), parody);
        audit.record("CHARACTER_UPDATED", TARGET, id, details(character));
        return CharacterResponse.of(character);
    }

    /** Also removes the character from all media (media_character cascades). */
    @Transactional
    public void delete(UUID id) {
        StoryCharacter character = find(id);
        Map<String, Object> details = details(character);
        characters.delete(character);
        audit.record("CHARACTER_DELETED", TARGET, id, details);
    }

    private void ensureUnique(String name, Parody parody, UUID excludeId) {
        UUID parodyId = parody == null ? null : parody.getId();
        if (characters.existsDuplicate(Names.normalize(name), parodyId, excludeId)) {
            String scope = parody == null ? "without a parody" : "in parody '" + parody.getName() + "'";
            throw new ConflictException("A character named '" + name.strip() + "' already exists " + scope + ".");
        }
    }

    private Parody parody(UUID parodyId) {
        if (parodyId == null) {
            return null;
        }
        return parodies.findById(parodyId)
                .orElseThrow(() -> new BadRequestException("Parody " + parodyId + " does not exist."));
    }

    private StoryCharacter find(UUID id) {
        return characters.findById(id).orElseThrow(() -> NotFoundException.of("Character", id));
    }

    private static Map<String, Object> details(StoryCharacter character) {
        Map<String, Object> details = new HashMap<>();
        details.put("name", character.getName());
        details.put("parody", character.getParody() == null ? null : character.getParody().getName());
        return details;
    }
}

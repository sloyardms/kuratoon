package dev.kuratoon.character;

import dev.kuratoon.character.CharacterDtos.CharacterRequest;
import dev.kuratoon.character.CharacterDtos.CharacterResponse;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/characters")
class CharacterAdminController {

    private final CharacterService service;

    CharacterAdminController(CharacterService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<CharacterResponse> list(
            @RequestParam(required = false) UUID parodyId,
            @RequestParam(required = false) String q,
            @Valid PageParams paging) {
        return service.list(parodyId, q, paging);
    }

    @GetMapping("/{id}")
    CharacterResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<CharacterResponse> create(@Valid @RequestBody CharacterRequest request) {
        CharacterResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/characters/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    CharacterResponse update(@PathVariable UUID id, @Valid @RequestBody CharacterRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package dev.kuratoon.tag;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.tag.TagDtos.TagRequest;
import dev.kuratoon.tag.TagDtos.TagResponse;
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
@RequestMapping("/api/v1/admin/tags")
class TagAdminController {

    private final TagService service;

    TagAdminController(TagService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<TagResponse> list(
            @RequestParam(required = false) TagKind kind,
            @RequestParam(required = false) String q,
            @Valid PageParams paging) {
        return service.list(kind, q, paging);
    }

    @GetMapping("/{id}")
    TagResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        TagResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/tags/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    TagResponse update(@PathVariable UUID id, @Valid @RequestBody TagRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

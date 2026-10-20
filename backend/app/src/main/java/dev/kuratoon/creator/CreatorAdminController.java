package dev.kuratoon.creator;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.creator.CreatorDtos.CreatorRequest;
import dev.kuratoon.creator.CreatorDtos.CreatorResponse;
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
@RequestMapping("/api/v1/admin/creators")
class CreatorAdminController {

    private final CreatorService service;

    CreatorAdminController(CreatorService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<CreatorResponse> list(@RequestParam(required = false) String q, @Valid PageParams paging) {
        return service.list(q, paging);
    }

    @GetMapping("/{id}")
    CreatorResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<CreatorResponse> create(@Valid @RequestBody CreatorRequest request) {
        CreatorResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/creators/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    CreatorResponse update(@PathVariable UUID id, @Valid @RequestBody CreatorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package dev.kuratoon.parody;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.parody.ParodyDtos.ParodyRequest;
import dev.kuratoon.parody.ParodyDtos.ParodyResponse;
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
@RequestMapping("/api/v1/admin/parodies")
class ParodyAdminController {

    private final ParodyService service;

    ParodyAdminController(ParodyService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<ParodyResponse> list(@RequestParam(required = false) String q, @Valid PageParams paging) {
        return service.list(q, paging);
    }

    @GetMapping("/{id}")
    ParodyResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<ParodyResponse> create(@Valid @RequestBody ParodyRequest request) {
        ParodyResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/parodies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    ParodyResponse update(@PathVariable UUID id, @Valid @RequestBody ParodyRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

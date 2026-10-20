package dev.kuratoon.mediatype;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.mediatype.MediaTypeDtos.MediaTypeRequest;
import dev.kuratoon.mediatype.MediaTypeDtos.MediaTypeResponse;
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
@RequestMapping("/api/v1/admin/media-types")
class MediaTypeAdminController {

    private final MediaTypeService service;

    MediaTypeAdminController(MediaTypeService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<MediaTypeResponse> list(@RequestParam(required = false) String q, @Valid PageParams paging) {
        return service.list(q, paging);
    }

    @GetMapping("/{id}")
    MediaTypeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<MediaTypeResponse> create(@Valid @RequestBody MediaTypeRequest request) {
        MediaTypeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/media-types/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    MediaTypeResponse update(@PathVariable UUID id, @Valid @RequestBody MediaTypeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

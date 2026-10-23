package dev.kuratoon.media;

import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.media.MediaAdminDtos.CoverCropRequest;
import dev.kuratoon.media.MediaAdminDtos.CoverResult;
import dev.kuratoon.media.MediaAdminDtos.LockedFieldsRequest;
import dev.kuratoon.media.MediaAdminDtos.MediaEditView;
import dev.kuratoon.media.MediaAdminDtos.MediaRow;
import dev.kuratoon.media.MediaAdminDtos.MediaUpdateRequest;
import dev.kuratoon.media.MediaAdminDtos.RelationsRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/media")
class MediaAdminController {

    private final MediaAdminService service;

    MediaAdminController(MediaAdminService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<MediaRow> list(
            @RequestParam(required = false) String q, @RequestParam(required = false) UUID type, @Valid PageParams paging) {
        return service.list(q, type, paging);
    }

    @GetMapping("/{id}")
    MediaEditView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    MediaEditView update(@PathVariable UUID id, @Valid @RequestBody MediaUpdateRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/locked-fields")
    MediaEditView setLockedFields(@PathVariable UUID id, @Valid @RequestBody LockedFieldsRequest request) {
        return service.setLockedFields(id, request.fields());
    }

    @PutMapping("/{id}/relations")
    MediaEditView setRelations(@PathVariable UUID id, @Valid @RequestBody RelationsRequest request) {
        return service.setRelations(id, request.relations());
    }

    @PutMapping("/{id}/cover-crop")
    CoverResult setCoverCrop(@PathVariable UUID id, @RequestBody CoverCropRequest request) {
        return service.setCoverCrop(id, request.crop());
    }

    /** Replacement cover upload (10 MB, the only upload route; nginx allows it only here). */
    @PostMapping(path = "/{id}/cover", consumes = "multipart/form-data")
    CoverResult replaceCover(@PathVariable UUID id, @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("The upload is empty.");
        }
        try (InputStream in = file.getInputStream()) {
            return service.replaceCover(id, file.getOriginalFilename(), in);
        }
    }

    /**
     * The original cover for the admin crop tool. The only image Spring serves: admin-only and
     * rare; everything else is served by nginx (docs/spec/06-images.md).
     */
    @GetMapping("/{id}/cover/original")
    ResponseEntity<Resource> originalCover(@PathVariable UUID id) {
        Path file = service.originalCover(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaTypeFactory.getMediaType(file.getFileName().toString())
                        .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
                .body(new FileSystemResource(file));
    }

    @DeleteMapping("/{id}/chapters/{chapterId}")
    MediaEditView deleteChapter(@PathVariable UUID id, @PathVariable UUID chapterId) {
        return service.deleteChapter(id, chapterId);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

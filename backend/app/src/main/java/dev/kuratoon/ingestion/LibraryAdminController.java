package dev.kuratoon.ingestion;

import dev.kuratoon.ingestion.LibraryMaintenanceService.IntegrityReport;
import dev.kuratoon.ingestion.LibraryMaintenanceService.RebuildResult;
import dev.kuratoon.ingestion.LibraryMaintenanceService.RegenerateResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Maintenance page API: integrity scan, thumbnail regeneration, rebuild from disk. */
@RestController
@RequestMapping("/api/v1/admin/library")
class LibraryAdminController {

    private final LibraryMaintenanceService service;

    LibraryAdminController(LibraryMaintenanceService service) {
        this.service = service;
    }

    record RegenerateRequest(@NotEmpty @Size(max = 500) List<UUID> mediaIds) {}

    @PostMapping("/integrity-scan")
    IntegrityReport scan() {
        return service.scan();
    }

    @PostMapping("/thumbnails")
    RegenerateResult regenerate(@Valid @RequestBody RegenerateRequest request) {
        return service.regenerateThumbnails(request.mediaIds());
    }

    @PostMapping("/rebuild")
    RebuildResult rebuild() {
        return service.rebuild();
    }
}

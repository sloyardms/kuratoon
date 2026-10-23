package dev.kuratoon.ingestion;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.ingestion.ImportAdminDtos.ConflictRequest;
import dev.kuratoon.ingestion.ImportAdminDtos.DuplicateRequest;
import dev.kuratoon.ingestion.ImportAdminDtos.ImportJobDetail;
import dev.kuratoon.ingestion.ImportAdminDtos.ImportJobSummary;
import dev.kuratoon.media.MediaFields;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/imports")
class ImportAdminController {

    private final ImportAdminService service;

    ImportAdminController(ImportAdminService service) {
        this.service = service;
    }

    /** @param status optional filter, repeatable or comma-separated */
    @GetMapping
    OffsetPage<ImportJobSummary> list(@RequestParam(required = false) List<ImportStatus> status, @Valid PageParams paging) {
        return service.list(status, paging);
    }

    @GetMapping("/{id}")
    ImportJobDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/retry")
    ImportJobDetail retry(@PathVariable UUID id) {
        return service.retry(id);
    }

    @PutMapping("/{id}/metadata")
    ImportJobDetail fillMetadata(@PathVariable UUID id, @RequestBody MediaFields fields) {
        return service.fillMetadata(id, fields);
    }

    @PostMapping("/{id}/conflicts")
    ImportJobDetail resolveConflicts(@PathVariable UUID id, @Valid @RequestBody ConflictRequest request) {
        return service.resolveConflicts(id, request);
    }

    @PostMapping("/{id}/duplicate")
    ImportJobDetail resolveDuplicate(@PathVariable UUID id, @Valid @RequestBody DuplicateRequest request) {
        return service.resolveDuplicate(id, request);
    }

    @PostMapping("/{id}/dismiss")
    ImportJobDetail dismiss(@PathVariable UUID id) {
        return service.dismiss(id);
    }
}

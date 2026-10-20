package dev.kuratoon.media;

import dev.kuratoon.media.MediaReadDtos.ChapterDetail;
import dev.kuratoon.media.MediaReadDtos.ChapterSummary;
import dev.kuratoon.media.MediaReadDtos.MediaDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read endpoints for the media detail page and the reader. */
@RestController
@RequestMapping("/api/v1")
class MediaController {

    private final MediaReadService service;

    MediaController(MediaReadService service) {
        this.service = service;
    }

    @GetMapping("/media/{id}")
    MediaDetail media(@PathVariable UUID id) {
        return service.detail(id);
    }

    /** All chapters in ascending number order; filtering and reversing happen client-side. */
    @GetMapping("/media/{id}/chapters")
    List<ChapterSummary> chapters(@PathVariable UUID id) {
        return service.chapters(id);
    }

    @GetMapping("/chapters/{id}")
    ChapterDetail chapter(@PathVariable UUID id) {
        return service.chapter(id);
    }
}

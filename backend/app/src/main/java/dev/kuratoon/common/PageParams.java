package dev.kuratoon.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Offset paging query parameters ({@code ?page=0&size=50}); size is capped at 100. */
public record PageParams(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {

    public static final int DEFAULT_SIZE = 50;

    public PageRequest toPageRequest(Sort sort) {
        return PageRequest.of(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size, sort);
    }
}

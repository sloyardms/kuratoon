package dev.kuratoon.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Offset-paginated response for admin tables (docs/spec/07-frontend.md). */
public record OffsetPage<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> OffsetPage<T> of(Page<E> page, Function<E, T> mapper) {
        return new OffsetPage<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

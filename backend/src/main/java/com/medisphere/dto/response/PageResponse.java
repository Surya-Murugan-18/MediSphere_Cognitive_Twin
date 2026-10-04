package com.medisphere.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic paginated response wrapper.
 * Use PageResponse.from(springPage, mapper) to convert Spring Data Pages.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean first,
        boolean last
) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast()
        );
    }
}

package com.soudan.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Phản hồi phân trang. {@code page} đếm từ 0. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}
}

package com.soudan.common.api;

import java.util.Map;

import com.soudan.common.exception.ValidationException;

/**
 * Tham số phân trang, sắp xếp, lọc từ request.
 * {@code sort} có dạng {@code "field"} hoặc {@code "field,desc"}.
 */
public record PageQuery(int page, int size, String sort, Map<String, String> filters) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	public PageQuery {
		if (page < 0) {
			throw new ValidationException("page phải >= 0");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new ValidationException("size phải từ 1 đến " + MAX_SIZE);
		}
		filters = filters == null ? Map.of() : Map.copyOf(filters);
	}
}

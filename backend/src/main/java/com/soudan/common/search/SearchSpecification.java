package com.soudan.common.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.api.PageQuery;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.search.SearchWhitelist.FilterRule;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Dựng {@link Specification} và {@link Pageable} từ {@link PageQuery} theo whitelist.
 * Giá trị người dùng luôn đi qua tham số của Criteria API, không bao giờ nối vào chuỗi truy vấn.
 */
public final class SearchSpecification {

	private SearchSpecification() {
	}

	public static <T> Specification<T> of(Map<String, String> filters, SearchWhitelist whitelist) {
		for (String name : filters.keySet()) {
			if (!whitelist.filters().containsKey(name)) {
				throw new ValidationException(ErrorCode.INVALID_FILTER, "Không được lọc theo '" + name + "'");
			}
		}
		Specification<T> spec = (root, query, cb) -> cb.conjunction();
		for (Map.Entry<String, String> entry : filters.entrySet()) {
			String value = entry.getValue();
			if (value == null || value.isBlank()) {
				continue;
			}
			spec = spec.and(rule(whitelist.filters().get(entry.getKey()), value.trim()));
		}
		return spec;
	}

	public static Pageable pageable(PageQuery query, SearchWhitelist whitelist, Sort defaultSort) {
		return PageRequest.of(query.page(), query.size(), sort(query.sort(), whitelist, defaultSort));
	}

	private static Sort sort(String raw, SearchWhitelist whitelist, Sort defaultSort) {
		if (raw == null || raw.isBlank()) {
			return defaultSort;
		}
		String[] parts = raw.split(",");
		String field = parts[0].trim();
		if (!whitelist.sortable().contains(field)) {
			throw new ValidationException(ErrorCode.INVALID_SORT, "Không được sắp xếp theo '" + field + "'");
		}
		boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
		return Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, field);
	}

	private static <T> Specification<T> rule(FilterRule rule, String value) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			for (String field : rule.fields()) {
				Path<?> path = root.get(field);
				if (rule.operator() == SearchWhitelist.Operator.CONTAINS) {
					Expression<String> text = path.as(String.class);
					predicates.add(cb.like(cb.lower(text), "%" + escapeLike(value.toLowerCase(Locale.ROOT)) + "%", '\\'));
				} else {
					predicates.add(cb.equal(path, convert(path.getJavaType(), value, field)));
				}
			}
			return cb.or(predicates.toArray(Predicate[]::new));
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object convert(Class<?> type, String value, String field) {
		try {
			if (type.isEnum()) {
				return Enum.valueOf((Class<Enum>) type, value.toUpperCase(Locale.ROOT));
			}
			if (type == Long.class || type == long.class) {
				return Long.valueOf(value);
			}
			if (type == Boolean.class || type == boolean.class) {
				return Boolean.valueOf(value);
			}
		} catch (IllegalArgumentException e) {
			throw new ValidationException(ErrorCode.INVALID_FILTER, "Giá trị lọc không hợp lệ cho '" + field + "'");
		}
		return value;
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}

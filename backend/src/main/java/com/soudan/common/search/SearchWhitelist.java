package com.soudan.common.search;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Danh sách trường được phép lọc và sắp xếp của một module. Mọi trường ngoài danh sách đều bị từ chối,
 * nên không bao giờ nối chuỗi từ request vào truy vấn.
 *
 * @param filters tên tham số lọc -> quy tắc lọc
 * @param sortable tên trường được phép sắp xếp (là tên thuộc tính của entity)
 */
public record SearchWhitelist(Map<String, FilterRule> filters, Set<String> sortable) {

	public enum Operator {
		/** So sánh bằng (kể cả enum). */
		EQUALS,
		/** Chứa chuỗi, không phân biệt hoa thường. Nhiều trường thì khớp một trong số đó. */
		CONTAINS
	}

	public record FilterRule(List<String> fields, Operator operator) {

		public static FilterRule equalsTo(String field) {
			return new FilterRule(List.of(field), Operator.EQUALS);
		}

		public static FilterRule contains(String... fields) {
			return new FilterRule(List.of(fields), Operator.CONTAINS);
		}
	}
}

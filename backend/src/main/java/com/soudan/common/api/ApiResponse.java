package com.soudan.common.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Khung phản hồi chung. Thành công: {@code {success:true, data}}.
 * Lỗi: {@code {success:false, code, message, details, timestamp}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		boolean success,
		T data,
		String code,
		String message,
		List<FieldDetail> details,
		Instant timestamp) {

	/** Một lỗi theo trường (dùng cho lỗi validation 422). */
	public record FieldDetail(String field, String message) {
	}

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null, null, null, null);
	}

	public static ApiResponse<Void> error(ErrorCode code, String message, List<FieldDetail> details, Instant now) {
		return new ApiResponse<>(false, null, code.name(), message, details, now);
	}
}

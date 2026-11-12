package com.soudan.common.exception;

import com.soudan.common.api.ErrorCode;

/** Dữ liệu sai (HTTP 422). */
public class ValidationException extends BusinessException {

	public ValidationException(String message) {
		super(ErrorCode.VALIDATION_ERROR, message);
	}

	public ValidationException(ErrorCode code, String message) {
		super(code, message);
	}
}

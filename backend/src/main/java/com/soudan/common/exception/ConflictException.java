package com.soudan.common.exception;

import com.soudan.common.api.ErrorCode;

/** Xung đột dữ liệu hoặc phiên bản (HTTP 409). */
public class ConflictException extends BusinessException {

	public ConflictException(ErrorCode code) {
		super(code);
	}

	public ConflictException(ErrorCode code, String message) {
		super(code, message);
	}
}

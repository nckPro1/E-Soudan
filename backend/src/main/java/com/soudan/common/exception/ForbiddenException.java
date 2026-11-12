package com.soudan.common.exception;

import com.soudan.common.api.ErrorCode;

/** Đã đăng nhập nhưng không được phép (HTTP 403). */
public class ForbiddenException extends BusinessException {

	public ForbiddenException(ErrorCode code) {
		super(code);
	}

	public ForbiddenException(String message) {
		super(ErrorCode.FORBIDDEN, message);
	}
}

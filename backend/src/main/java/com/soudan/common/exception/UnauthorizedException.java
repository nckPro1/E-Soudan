package com.soudan.common.exception;

import com.soudan.common.api.ErrorCode;

/** Chưa xác thực hoặc thông tin xác thực sai (HTTP 401). */
public class UnauthorizedException extends BusinessException {

	public UnauthorizedException(ErrorCode code) {
		super(code);
	}
}

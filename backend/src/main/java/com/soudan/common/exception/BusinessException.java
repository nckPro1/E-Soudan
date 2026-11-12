package com.soudan.common.exception;

import com.soudan.common.api.ErrorCode;

/** Vi phạm quy tắc nghiệp vụ. Lớp con chỉ khác nhau ở mã lỗi mặc định. */
public class BusinessException extends RuntimeException {

	private final ErrorCode code;

	public BusinessException(ErrorCode code) {
		super(code.defaultMessage());
		this.code = code;
	}

	public BusinessException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}

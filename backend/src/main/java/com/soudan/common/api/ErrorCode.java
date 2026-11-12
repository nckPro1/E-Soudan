package com.soudan.common.api;

import org.springframework.http.HttpStatus;

/** Mã lỗi tập trung. Mỗi mã gắn với một HTTP status (ARCHITECTURE.md 5.2). */
public enum ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc phiên không hợp lệ"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
	ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá"),
	PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Cần đổi mật khẩu trước khi tiếp tục"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền thực hiện"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
	CONFLICT(HttpStatus.CONFLICT, "Xung đột dữ liệu"),
	VERSION_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu vừa được người khác thay đổi, hãy tải lại"),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
	LAST_ADMIN_PROTECTED(HttpStatus.CONFLICT, "Không thể khoá hoặc hạ quyền Admin cuối cùng"),
	VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "Dữ liệu không hợp lệ"),
	CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_CONTENT, "Mật khẩu hiện tại không đúng"),
	INVALID_FILTER(HttpStatus.UNPROCESSABLE_CONTENT, "Trường lọc không được phép"),
	INVALID_SORT(HttpStatus.UNPROCESSABLE_CONTENT, "Trường sắp xếp không được phép"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}

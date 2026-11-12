package com.soudan.common.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.api.ApiResponse.FieldDetail;
import com.soudan.common.api.ErrorCode;

/**
 * Gom toàn bộ xử lý lỗi vào một chỗ (ARCHITECTURE.md 5.2); controller không tự bắt exception.
 * Bộ lọc bảo mật cũng uỷ quyền cho bean này qua HandlerExceptionResolver để 401/403 cùng định dạng.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final Clock clock;

	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		return build(e.getCode(), e.getMessage(), null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
		List<FieldDetail> details = e.getBindingResult().getFieldErrors().stream()
				.map(f -> new FieldDetail(f.getField(), f.getDefaultMessage()))
				.toList();
		return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiResponse<Void>> handleParamValidation(HandlerMethodValidationException e) {
		return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), null);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class })
	ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
		return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage(), null);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
		return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.defaultMessage(), null);
	}

	@ExceptionHandler(MultipartException.class)
	ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e) {
		return build(ErrorCode.INVALID_FILE, ErrorCode.INVALID_FILE.defaultMessage(), null);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
		return build(ErrorCode.VERSION_CONFLICT, ErrorCode.VERSION_CONFLICT.defaultMessage(), null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
		log.warn("Vi phạm ràng buộc dữ liệu", e);
		return build(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage(), null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage(), null);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
		return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
		return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return build(ErrorCode.BAD_REQUEST, "Phương thức HTTP không được hỗ trợ", null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("Lỗi không lường trước", e);
		return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
	}

	private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, List<FieldDetail> details) {
		return ResponseEntity.status(code.status())
				.body(ApiResponse.error(code, message, details, Instant.now(clock)));
	}
}

package com.soudan.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.soudan.auth.JwtAuthenticationFilter;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Trả lỗi cho request chưa xác thực. Uỷ quyền cho {@code GlobalExceptionHandler} qua HandlerExceptionResolver
 * để 401/403 từ bộ lọc có cùng định dạng {@code ApiResponse} với lỗi trong controller.
 */
@Component
class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver resolver;

	RestAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException, ServletException {
		Object reason = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE);
		ErrorCode code = reason instanceof ErrorCode c ? c : ErrorCode.UNAUTHORIZED;
		resolver.resolveException(request, response, null, new BusinessException(code));
	}
}

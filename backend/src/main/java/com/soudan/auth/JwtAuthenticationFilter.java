package com.soudan.auth;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.soudan.auth.service.JwtService;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.UnauthorizedException;
import com.soudan.common.security.AuthenticatedUser;
import com.soudan.user.UserAccount;
import com.soudan.user.UserApi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Đọc {@code Authorization: Bearer ...}, kiểm tra token rồi đặt người dùng vào SecurityContext.
 * Mỗi request đọc lại tài khoản từ CSDL: tài khoản bị khoá hoặc đổi vai trò giữa phiên có hiệu lực ngay.
 * Khi không xác thực được, bộ lọc chỉ ghi mã lỗi vào request; entry point của Security sẽ trả 401/403 đúng định dạng.
 * Đặt ở package gốc của module auth để config (SecurityConfig) dùng được.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	/** Tên thuộc tính request mang {@link ErrorCode} khi xác thực thất bại. */
	public static final String ERROR_ATTRIBUTE = "soudan.auth.error";

	private static final String BEARER = "Bearer ";
	/** Khi buộc đổi mật khẩu, chỉ cho gọi các đường dẫn này. */
	private static final Set<String> ALLOWED_WHEN_PASSWORD_CHANGE_REQUIRED = Set.of(
			"/api/v1/me", "/api/v1/me/change-password");

	private final JwtService jwtService;
	private final UserApi userApi;

	public JwtAuthenticationFilter(JwtService jwtService, UserApi userApi) {
		this.jwtService = jwtService;
		this.userApi = userApi;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER)) {
			authenticate(request, header.substring(BEARER.length()).trim());
		}
		chain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, String token) {
		try {
			Long userId = jwtService.parseUserId(token);
			UserAccount account = userApi.findAccountById(userId).orElse(null);
			if (account == null || !account.active()) {
				request.setAttribute(ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
				return;
			}
			if (account.mustChangePassword() && !ALLOWED_WHEN_PASSWORD_CHANGE_REQUIRED.contains(request.getRequestURI())) {
				request.setAttribute(ERROR_ATTRIBUTE, ErrorCode.PASSWORD_CHANGE_REQUIRED);
				return;
			}
			AuthenticatedUser principal = new AuthenticatedUser(account.id(), account.email(), account.systemRole());
			var authentication = new UsernamePasswordAuthenticationToken(principal, null,
					List.of(new SimpleGrantedAuthority("ROLE_" + account.systemRole().name())));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (UnauthorizedException e) {
			request.setAttribute(ERROR_ATTRIBUTE, e.getCode());
		}
	}
}

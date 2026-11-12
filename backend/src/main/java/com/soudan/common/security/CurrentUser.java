package com.soudan.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Lấy người dùng của request hiện tại. */
@Component
public class CurrentUser {

	public Optional<AuthenticatedUser> find() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return Optional.of(user);
		}
		return Optional.empty();
	}

	/** Dùng ở chỗ chắc chắn đã đăng nhập (endpoint yêu cầu xác thực). */
	public AuthenticatedUser require() {
		return find().orElseThrow(() -> new IllegalStateException("Không có người dùng đăng nhập"));
	}
}

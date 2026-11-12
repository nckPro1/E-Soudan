package com.soudan.user.web;

import java.time.Instant;

import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.UserStatus;

/** Thông tin tài khoản trả ra ngoài. Không bao giờ chứa mật khẩu. {@code version} để client gửi lại khi sửa. */
public record UserResponse(
		Long id,
		String email,
		String fullName,
		String position,
		SystemRole systemRole,
		UserStatus status,
		boolean mustChangePassword,
		Long version,
		Instant createdAt) {
}

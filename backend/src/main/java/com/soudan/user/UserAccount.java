package com.soudan.user;

import com.soudan.common.security.SystemRole;

/** Ảnh chụp thông tin tài khoản để module khác dùng xác thực (không lộ entity). */
public record UserAccount(
		Long id,
		String email,
		String passwordHash,
		String fullName,
		SystemRole systemRole,
		boolean active,
		boolean mustChangePassword) {
}

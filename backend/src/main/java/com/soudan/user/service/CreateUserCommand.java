package com.soudan.user.service;

import com.soudan.common.security.SystemRole;

/** Dữ liệu để tạo tài khoản; {@code password} là mật khẩu tạm dạng thô (sẽ được băm). */
public record CreateUserCommand(String email, String password, String fullName, String position,
		SystemRole systemRole) {
}

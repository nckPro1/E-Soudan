package com.soudan.user.web;

import com.soudan.common.security.SystemRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Email @Size(max = 255) String email,
		// BCrypt chỉ dùng tối đa 72 byte đầu của mật khẩu
		@NotBlank @Size(min = 8, max = 72) String temporaryPassword,
		@NotBlank @Size(max = 100) String fullName,
		@Size(max = 100) String position,
		@NotNull SystemRole systemRole) {
}

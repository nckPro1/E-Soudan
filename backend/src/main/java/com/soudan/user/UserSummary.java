package com.soudan.user;

import com.soudan.common.security.SystemRole;

/** Thông tin tối thiểu về một người dùng cho module khác (hiển thị tên thành viên, gửi email thông báo). */
public record UserSummary(Long id, String fullName, String email, String position, SystemRole systemRole,
		boolean active) {
}

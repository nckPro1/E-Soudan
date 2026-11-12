package com.soudan.user;

import com.soudan.common.security.SystemRole;

/** Thông tin tối thiểu về một người dùng cho module khác (ví dụ team hiển thị tên thành viên). */
public record UserSummary(Long id, String fullName, String position, SystemRole systemRole, boolean active) {
}

package com.soudan.common.security;

/**
 * Vai trò trong một nhóm (tầng thứ hai, khác {@link SystemRole}). Đặt ở common vì AccessPolicy cần dùng
 * mà không được phụ thuộc ngược vào module team.
 */
public enum TeamRole {
	LEADER,
	MEMBER,
	OBSERVER
}

package com.soudan.common.security;

/**
 * Vai trò hệ thống, gắn với tài khoản. Đặt ở common vì cả module user và auth đều dùng,
 * mà common không được phụ thuộc module nghiệp vụ (ARCHITECTURE.md 4.2).
 */
public enum SystemRole {
	ADMIN,
	MANAGER,
	USER
}

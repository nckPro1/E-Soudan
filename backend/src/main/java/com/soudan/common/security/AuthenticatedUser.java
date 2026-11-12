package com.soudan.common.security;

/** Người dùng đã xác thực của request hiện tại (principal trong SecurityContext). */
public record AuthenticatedUser(Long id, String email, SystemRole role) {
}

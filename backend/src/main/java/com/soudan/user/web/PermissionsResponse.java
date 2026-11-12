package com.soudan.user.web;

import java.util.Set;

import com.soudan.common.security.SystemRole;

/** Quyền của người dùng hiện tại, để frontend ẩn/hiện giao diện. */
public record PermissionsResponse(SystemRole systemRole, Set<String> permissions) {
}

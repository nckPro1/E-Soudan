package com.soudan.common.security;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Nơi tập trung các kiểm tra quyền. Dùng trong {@code @PreAuthorize("@accessPolicy.isAdmin()")}.
 * Các stage sau thêm hàm theo nhóm (isTeamLeader, canViewReport...).
 */
@Component("accessPolicy")
public class AccessPolicy {

	private static final Set<String> ADMIN_PERMISSIONS = Set.of(
			"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_LOCK", "USER_ASSIGN_ROLE");

	private final CurrentUser currentUser;

	public AccessPolicy(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	public boolean isAdmin() {
		return currentUser.find().map(u -> u.role() == SystemRole.ADMIN).orElse(false);
	}

	public boolean isManager() {
		return currentUser.find().map(u -> u.role() == SystemRole.MANAGER).orElse(false);
	}

	/** Danh sách quyền gửi cho frontend (chỉ để ẩn/hiện giao diện; backend vẫn kiểm tra thật). */
	public Set<String> permissionsOf(SystemRole role) {
		return role == SystemRole.ADMIN ? ADMIN_PERMISSIONS : Set.of();
	}
}

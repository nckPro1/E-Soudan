package com.soudan.common.security;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Nơi tập trung các kiểm tra quyền. Dùng trong {@code @PreAuthorize("@accessPolicy.isAdmin()")}.
 * Các stage sau thêm hàm theo nhóm (canViewReport...).
 */
@Component("accessPolicy")
public class AccessPolicy {

	private static final Set<String> ADMIN_PERMISSIONS = Set.of(
			"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_LOCK", "USER_ASSIGN_ROLE", "TEAM_MANAGE");
	private static final Set<String> MANAGER_PERMISSIONS = Set.of("USER_READ");

	private final CurrentUser currentUser;
	private final TeamMembershipLookup memberships;

	public AccessPolicy(CurrentUser currentUser, TeamMembershipLookup memberships) {
		this.currentUser = currentUser;
		this.memberships = memberships;
	}

	public boolean isAdmin() {
		return currentUser.find().map(u -> u.role() == SystemRole.ADMIN).orElse(false);
	}

	public boolean isManager() {
		return currentUser.find().map(u -> u.role() == SystemRole.MANAGER).orElse(false);
	}

	/** Xem danh sách nhân sự: Admin và Manager (Manager chỉ xem). */
	public boolean canReadUsers() {
		return isAdmin() || isManager();
	}

	public boolean isTeamLeader(Long teamId) {
		return teamRole(teamId).map(r -> r == TeamRole.LEADER).orElse(false);
	}

	/** Là thành viên của nhóm, gồm cả Leader (không tính OBSERVER). */
	public boolean isTeamMember(Long teamId) {
		return teamRole(teamId).map(r -> r == TeamRole.LEADER || r == TeamRole.MEMBER).orElse(false);
	}

	public boolean isTeamObserver(Long teamId) {
		return teamRole(teamId).map(r -> r == TeamRole.OBSERVER).orElse(false);
	}

	/** Admin hoặc bất kỳ ai có mặt trong nhóm (Leader, Member, Observer) xem được nhóm. */
	public boolean canViewTeam(Long teamId) {
		return isAdmin() || teamRole(teamId).isPresent();
	}

	/** Danh sách quyền gửi cho frontend (chỉ để ẩn/hiện giao diện; backend vẫn kiểm tra thật). */
	public Set<String> permissionsOf(SystemRole role) {
		return switch (role) {
			case ADMIN -> new HashSet<>(ADMIN_PERMISSIONS);
			case MANAGER -> new HashSet<>(MANAGER_PERMISSIONS);
			case USER -> Set.of();
		};
	}

	private Optional<TeamRole> teamRole(Long teamId) {
		return currentUser.find().flatMap(u -> memberships.roleOf(teamId, u.id()));
	}
}

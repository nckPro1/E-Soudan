package com.soudan.team.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.security.TeamRole;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Nhóm làm việc. Các quy tắc không cần dữ liệu ngoài nằm ở đây: nhóm đã lưu trữ không đổi được,
 * không thêm trùng người, luôn còn ít nhất một Leader. Quy tắc cần biết vai trò hệ thống của người dùng
 * (ai được làm Leader/Observer) nằm ở TeamService.
 */
@Entity
@Table(name = "team")
@SQLRestriction("deleted_at IS NULL")
public class Team extends BaseEntity {

	@Column(nullable = false)
	private String name;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeamStatus status;

	@OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
	@BatchSize(size = 50)
	private List<TeamMember> members = new ArrayList<>();

	protected Team() {
		// JPA
	}

	public static Team create(String name, String description) {
		Team team = new Team();
		team.name = name.trim();
		team.description = blankToNull(description);
		team.status = TeamStatus.ACTIVE;
		return team;
	}

	public void updateInfo(String name, String description) {
		assertActive();
		this.name = name.trim();
		this.description = blankToNull(description);
	}

	public void archive() {
		assertActive();
		this.status = TeamStatus.ARCHIVED;
	}

	public TeamMember addMember(Long userId, TeamRole role, Instant now) {
		assertActive();
		if (find(userId) != null) {
			throw new ConflictException(ErrorCode.ALREADY_TEAM_MEMBER);
		}
		TeamMember member = new TeamMember(this, userId, role, now);
		members.add(member);
		return member;
	}

	public void removeMember(Long userId) {
		assertActive();
		TeamMember member = require(userId);
		if (member.getTeamRole() == TeamRole.LEADER) {
			assertAnotherLeaderExists(member);
		}
		members.remove(member);
	}

	public void changeMemberRole(Long userId, TeamRole role) {
		assertActive();
		TeamMember member = require(userId);
		if (member.getTeamRole() == role) {
			return;
		}
		if (member.getTeamRole() == TeamRole.LEADER) {
			assertAnotherLeaderExists(member);
		}
		member.changeRole(role);
	}

	public long leaderCount() {
		return members.stream().filter(m -> m.getTeamRole() == TeamRole.LEADER).count();
	}

	public boolean isArchived() {
		return status == TeamStatus.ARCHIVED;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public TeamStatus getStatus() {
		return status;
	}

	public List<TeamMember> getMembers() {
		return Collections.unmodifiableList(members);
	}

	private void assertActive() {
		if (isArchived()) {
			throw new ConflictException(ErrorCode.TEAM_ARCHIVED);
		}
	}

	private void assertAnotherLeaderExists(TeamMember leaving) {
		boolean another = members.stream()
				.anyMatch(m -> m != leaving && m.getTeamRole() == TeamRole.LEADER);
		if (!another) {
			throw new ConflictException(ErrorCode.LAST_LEADER_PROTECTED);
		}
	}

	private TeamMember find(Long userId) {
		return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null);
	}

	private TeamMember require(Long userId) {
		TeamMember member = find(userId);
		if (member == null) {
			throw new NotFoundException("Người này không có trong nhóm");
		}
		return member;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

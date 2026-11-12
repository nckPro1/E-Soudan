package com.soudan.team.domain;

import java.time.Instant;

import com.soudan.common.security.TeamRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Một người trong một nhóm. {@code userId} chỉ là số khoá ngoại: module team không giữ entity của module user
 * (ARCHITECTURE.md 4.2). Thêm/xoá/đổi vai chỉ làm qua {@link Team} để quy tắc luôn được kiểm tra.
 */
@Entity
@Table(name = "team_member")
public class TeamMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id")
	private Team team;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "team_role", nullable = false)
	private TeamRole teamRole;

	@Column(nullable = false)
	private Instant joinedAt;

	protected TeamMember() {
		// JPA
	}

	TeamMember(Team team, Long userId, TeamRole teamRole, Instant joinedAt) {
		this.team = team;
		this.userId = userId;
		this.teamRole = teamRole;
		this.joinedAt = joinedAt;
	}

	void changeRole(TeamRole role) {
		this.teamRole = role;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public TeamRole getTeamRole() {
		return teamRole;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}

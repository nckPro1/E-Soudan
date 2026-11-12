package com.soudan.team.web;

import java.time.Instant;
import java.util.List;

import com.soudan.common.security.TeamRole;
import com.soudan.team.domain.TeamStatus;

public record TeamDetailResponse(Long id, String name, String description, TeamStatus status, Long version,
		Instant createdAt, List<MemberResponse> members) {

	public record MemberResponse(Long userId, String fullName, String position, TeamRole teamRole, Instant joinedAt) {
	}
}

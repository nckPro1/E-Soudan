package com.soudan.team.web;

import java.time.Instant;

import com.soudan.team.domain.TeamStatus;

/** Dòng trong danh sách nhóm. */
public record TeamResponse(Long id, String name, String description, TeamStatus status, int memberCount, Long version,
		Instant createdAt) {
}

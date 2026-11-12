package com.soudan.team.service;

import java.time.Instant;
import java.util.List;

import com.soudan.common.security.TeamRole;
import com.soudan.team.domain.TeamStatus;

/** Chi tiết nhóm kèm thành viên (đã ghép tên từ module user). Dựng trong giao dịch nên không dính lazy loading. */
public record TeamDetail(Long id, String name, String description, TeamStatus status, Long version, Instant createdAt,
		List<MemberDetail> members) {

	public record MemberDetail(Long userId, String fullName, String position, TeamRole teamRole, Instant joinedAt) {
	}
}

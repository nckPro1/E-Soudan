package com.soudan.team.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.security.TeamMembershipLookup;
import com.soudan.common.security.TeamRole;
import com.soudan.team.domain.TeamMember;
import com.soudan.team.repository.TeamMemberRepository;

/** Trả lời cho AccessPolicy: người này giữ vai trò gì trong nhóm. */
@Service
@Transactional(readOnly = true)
public class TeamMembershipService implements TeamMembershipLookup {

	private final TeamMemberRepository members;

	public TeamMembershipService(TeamMemberRepository members) {
		this.members = members;
	}

	@Override
	public Optional<TeamRole> roleOf(Long teamId, Long userId) {
		if (teamId == null || userId == null) {
			return Optional.empty();
		}
		return members.findByTeamIdAndUserId(teamId, userId).map(TeamMember::getTeamRole);
	}
}

package com.soudan.team.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.common.security.TeamRole;
import com.soudan.team.domain.TeamMember;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

	Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

	List<TeamMember> findByTeamId(Long teamId);

	List<TeamMember> findByUserId(Long userId);

	long countByTeamIdAndTeamRole(Long teamId, TeamRole teamRole);
}

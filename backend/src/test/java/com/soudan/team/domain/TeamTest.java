package com.soudan.team.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.security.TeamRole;

/** Quy tắc nằm trong domain Team: không cần Spring hay DB. */
class TeamTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	private static Team teamWithLeader(Long leaderId) {
		Team team = Team.create("Alpha", null);
		team.addMember(leaderId, TeamRole.LEADER, NOW);
		return team;
	}

	private static ErrorCode codeOf(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getCode();
		}
		return null;
	}

	@Test
	void should_startActive_when_created() {
		Team team = Team.create("  Alpha  ", "  ");

		assertThat(team.getName()).isEqualTo("Alpha");
		assertThat(team.getDescription()).isNull();
		assertThat(team.getStatus()).isEqualTo(TeamStatus.ACTIVE);
	}

	@Test
	void should_rejectDuplicate_when_sameUserAddedTwice() {
		Team team = teamWithLeader(1L);

		assertThat(codeOf(() -> team.addMember(1L, TeamRole.MEMBER, NOW))).isEqualTo(ErrorCode.ALREADY_TEAM_MEMBER);
	}

	@Test
	void should_protectLastLeader_when_removing() {
		Team team = teamWithLeader(1L);
		team.addMember(2L, TeamRole.MEMBER, NOW);

		assertThat(codeOf(() -> team.removeMember(1L))).isEqualTo(ErrorCode.LAST_LEADER_PROTECTED);
		assertThat(team.getMembers()).hasSize(2);
	}

	@Test
	void should_protectLastLeader_when_demoting() {
		Team team = teamWithLeader(1L);

		assertThat(codeOf(() -> team.changeMemberRole(1L, TeamRole.MEMBER))).isEqualTo(ErrorCode.LAST_LEADER_PROTECTED);
		assertThat(team.leaderCount()).isEqualTo(1);
	}

	@Test
	void should_allowRemovingLeader_when_anotherLeaderExists() {
		Team team = teamWithLeader(1L);
		team.addMember(2L, TeamRole.LEADER, NOW);

		team.removeMember(1L);

		assertThat(team.getMembers()).extracting(TeamMember::getUserId).containsExactly(2L);
	}

	@Test
	void should_allowDemotingLeader_when_anotherLeaderExists() {
		Team team = teamWithLeader(1L);
		team.addMember(2L, TeamRole.LEADER, NOW);

		team.changeMemberRole(1L, TeamRole.MEMBER);

		assertThat(team.leaderCount()).isEqualTo(1);
	}

	@Test
	void should_failWithNotFound_when_removingStranger() {
		Team team = teamWithLeader(1L);

		assertThat(codeOf(() -> team.removeMember(99L))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void should_rejectEveryChange_when_archived() {
		Team team = teamWithLeader(1L);
		team.addMember(2L, TeamRole.MEMBER, NOW);
		team.archive();

		assertThat(codeOf(() -> team.addMember(3L, TeamRole.MEMBER, NOW))).isEqualTo(ErrorCode.TEAM_ARCHIVED);
		assertThat(codeOf(() -> team.removeMember(2L))).isEqualTo(ErrorCode.TEAM_ARCHIVED);
		assertThat(codeOf(() -> team.changeMemberRole(2L, TeamRole.LEADER))).isEqualTo(ErrorCode.TEAM_ARCHIVED);
		assertThat(codeOf(() -> team.updateInfo("Beta", null))).isEqualTo(ErrorCode.TEAM_ARCHIVED);
		assertThat(codeOf(team::archive)).isEqualTo(ErrorCode.TEAM_ARCHIVED);
	}
}

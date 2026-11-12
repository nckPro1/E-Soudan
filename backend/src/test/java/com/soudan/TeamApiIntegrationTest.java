package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;

class TeamApiIntegrationTest extends IntegrationTestBase {

	private User leaderA;
	private User leaderB;
	private User member;
	private User outsider;
	private User manager;
	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		leaderA = createUser("leader-a@example.com", SystemRole.USER);
		leaderB = createUser("leader-b@example.com", SystemRole.USER);
		member = createUser("member@example.com", SystemRole.USER);
		outsider = createUser("outsider@example.com", SystemRole.USER);
		manager = createUser("manager@example.com", SystemRole.MANAGER);
		createUser("manager2@example.com", SystemRole.MANAGER);
		adminToken = accessTokenOf("admin@example.com");
	}

	// ---- helpers ----

	private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", bearer(token));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private static String createBody(String name, Long[] leaders, Long[] members, Long[] observers) {
		return "{\"name\":\"%s\",\"leaderIds\":%s,\"memberIds\":%s,\"observerIds\":%s}"
				.formatted(name, ids(leaders), ids(members), ids(observers));
	}

	private static String ids(Long[] values) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.length; i++) {
			sb.append(i == 0 ? "" : ",").append(values[i]);
		}
		return sb.append("]").toString();
	}

	private static final Long[] NONE = {};

	/** Admin tạo nhóm và trả về id. */
	private long createTeam(String name, User leader, Long[] members, Long[] observers) throws Exception {
		String json = send(post("/api/v1/teams"), adminToken,
				createBody(name, new Long[] { leader.getId() }, members, observers))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	private long version(long teamId) throws Exception {
		String json = send(get("/api/v1/teams/" + teamId), adminToken, null)
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.version")).longValue();
	}

	// ---- tạo nhóm ----

	@Test
	void should_createTeamWithAllRoles_when_adminCreates() throws Exception {
		send(post("/api/v1/teams"), adminToken, createBody("Alpha",
				new Long[] { leaderA.getId() }, new Long[] { member.getId() }, new Long[] { manager.getId() }))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("Alpha"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.members.length()").value(3))
				.andExpect(jsonPath("$.data.members[0].teamRole").value("LEADER"));
	}

	@Test
	void should_reject_when_noLeader() throws Exception {
		send(post("/api/v1/teams"), adminToken, createBody("Alpha", NONE, new Long[] { member.getId() }, NONE))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void should_reject_when_adminJoinsTeam() throws Exception {
		User admin = userRepository.findByEmailIgnoreCase("admin@example.com").orElseThrow();

		send(post("/api/v1/teams"), adminToken, createBody("Alpha", new Long[] { admin.getId() }, NONE, NONE))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TEAM_MEMBER"));
	}

	@Test
	void should_reject_when_managerIsMemberOrUserIsObserver() throws Exception {
		send(post("/api/v1/teams"), adminToken, createBody("Alpha", new Long[] { leaderA.getId() },
				new Long[] { manager.getId() }, NONE))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TEAM_MEMBER"));

		send(post("/api/v1/teams"), adminToken, createBody("Alpha", new Long[] { leaderA.getId() },
				NONE, new Long[] { member.getId() }))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TEAM_MEMBER"));
	}

	@Test
	void should_reject_when_lockedUserAdded() throws Exception {
		userService.lock(member.getId());

		send(post("/api/v1/teams"), adminToken, createBody("Alpha", new Long[] { leaderA.getId() },
				new Long[] { member.getId() }, NONE))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TEAM_MEMBER"));
	}

	@Test
	void should_reject_when_samePersonListedTwice() throws Exception {
		send(post("/api/v1/teams"), adminToken, createBody("Alpha", new Long[] { leaderA.getId() },
				new Long[] { leaderA.getId() }, NONE))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void should_reject_when_activeNameAlreadyTaken() throws Exception {
		createTeam("Alpha", leaderA, NONE, NONE);

		send(post("/api/v1/teams"), adminToken, createBody("alpha", new Long[] { leaderB.getId() }, NONE, NONE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TEAM_NAME_ALREADY_EXISTS"));
	}

	@Test
	void should_allowSameName_when_oldTeamArchived() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, NONE);
		send(post("/api/v1/teams/" + id + "/archive"), adminToken, "{\"version\":" + version(id) + "}")
				.andExpect(status().isOk());

		send(post("/api/v1/teams"), adminToken, createBody("Alpha", new Long[] { leaderB.getId() }, NONE, NONE))
				.andExpect(status().isCreated());
	}

	@Test
	void should_allowOnePersonToLeadTwoTeams() throws Exception {
		createTeam("Alpha", leaderA, NONE, NONE);

		send(post("/api/v1/teams"), adminToken, createBody("Beta", new Long[] { leaderA.getId() },
				new Long[] { member.getId() }, NONE))
				.andExpect(status().isCreated());
	}

	// ---- quyền ----

	@Test
	void should_forbidManagersAndUsersToWrite_when_notAdmin() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, new Long[] { manager.getId() });
		String managerToken = accessTokenOf("manager@example.com");
		String leaderToken = accessTokenOf("leader-a@example.com");
		String body = createBody("Beta", new Long[] { leaderB.getId() }, NONE, NONE);

		for (String token : new String[] { managerToken, leaderToken }) {
			send(post("/api/v1/teams"), token, body).andExpect(status().isForbidden());
			send(put("/api/v1/teams/" + id), token, "{\"name\":\"X\",\"version\":0}").andExpect(status().isForbidden());
			send(post("/api/v1/teams/" + id + "/archive"), token, "{\"version\":0}").andExpect(status().isForbidden());
			send(post("/api/v1/teams/" + id + "/members"), token,
					"{\"userId\":%d,\"teamRole\":\"MEMBER\"}".formatted(member.getId())).andExpect(status().isForbidden());
			send(delete("/api/v1/teams/" + id + "/members/" + leaderA.getId()), token, null)
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void should_returnUnauthorized_when_anonymous() throws Exception {
		mockMvc.perform(get("/api/v1/teams")).andExpect(status().isUnauthorized());
	}

	@Test
	void should_letInvolvedPeopleViewTeam_andBlockOthers() throws Exception {
		long id = createTeam("Alpha", leaderA, new Long[] { member.getId() }, new Long[] { manager.getId() });

		for (String email : new String[] { "leader-a@example.com", "member@example.com", "manager@example.com" }) {
			send(get("/api/v1/teams/" + id), accessTokenOf(email), null).andExpect(status().isOk());
		}
		send(get("/api/v1/teams/" + id), accessTokenOf("outsider@example.com"), null).andExpect(status().isForbidden());
		send(get("/api/v1/teams/" + id), accessTokenOf("manager2@example.com"), null).andExpect(status().isForbidden());
	}

	@Test
	void should_blockLeaderOfTeamAFromTeamB() throws Exception {
		createTeam("Alpha", leaderA, NONE, NONE);
		long teamB = createTeam("Beta", leaderB, NONE, NONE);

		send(get("/api/v1/teams/" + teamB), accessTokenOf("leader-a@example.com"), null)
				.andExpect(status().isForbidden());
		send(put("/api/v1/teams/" + teamB), accessTokenOf("leader-a@example.com"),
				"{\"name\":\"Hacked\",\"version\":0}").andExpect(status().isForbidden());
	}

	@Test
	void should_listOnlyOwnTeams_when_notAdmin() throws Exception {
		createTeam("Alpha", leaderA, new Long[] { member.getId() }, new Long[] { manager.getId() });
		createTeam("Beta", leaderB, NONE, NONE);

		send(get("/api/v1/teams"), adminToken, null)
				.andExpect(jsonPath("$.data.totalItems").value(2));
		send(get("/api/v1/teams"), accessTokenOf("member@example.com"), null)
				.andExpect(jsonPath("$.data.totalItems").value(1))
				.andExpect(jsonPath("$.data.items[0].name").value("Alpha"))
				.andExpect(jsonPath("$.data.items[0].memberCount").value(3));
		send(get("/api/v1/teams"), accessTokenOf("manager@example.com"), null)
				.andExpect(jsonPath("$.data.totalItems").value(1));
		send(get("/api/v1/teams"), accessTokenOf("outsider@example.com"), null)
				.andExpect(jsonPath("$.data.totalItems").value(0));
	}

	@Test
	void should_hideArchivedTeams_byDefault() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, NONE);
		send(post("/api/v1/teams/" + id + "/archive"), adminToken, "{\"version\":" + version(id) + "}")
				.andExpect(status().isOk());

		send(get("/api/v1/teams"), adminToken, null).andExpect(jsonPath("$.data.totalItems").value(0));
		send(get("/api/v1/teams?status=ARCHIVED"), adminToken, null).andExpect(jsonPath("$.data.totalItems").value(1));
	}

	@Test
	void should_rejectUnknownSort_when_listing() throws Exception {
		send(get("/api/v1/teams?sort=version"), adminToken, null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

	// ---- thành viên ----

	@Test
	void should_addChangeAndRemoveMember() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, NONE);

		send(post("/api/v1/teams/" + id + "/members"), adminToken,
				"{\"userId\":%d,\"teamRole\":\"MEMBER\"}".formatted(member.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.members.length()").value(2));

		send(put("/api/v1/teams/" + id + "/members/" + member.getId()), adminToken, "{\"teamRole\":\"LEADER\"}")
				.andExpect(status().isOk());

		send(delete("/api/v1/teams/" + id + "/members/" + leaderA.getId()), adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.members.length()").value(1));
	}

	@Test
	void should_reject_when_addingSamePersonTwice() throws Exception {
		long id = createTeam("Alpha", leaderA, new Long[] { member.getId() }, NONE);

		send(post("/api/v1/teams/" + id + "/members"), adminToken,
				"{\"userId\":%d,\"teamRole\":\"MEMBER\"}".formatted(member.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_TEAM_MEMBER"));
	}

	@Test
	void should_protectLastLeader_when_removingOrDemoting() throws Exception {
		long id = createTeam("Alpha", leaderA, new Long[] { member.getId() }, NONE);

		send(delete("/api/v1/teams/" + id + "/members/" + leaderA.getId()), adminToken, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LAST_LEADER_PROTECTED"));
		send(put("/api/v1/teams/" + id + "/members/" + leaderA.getId()), adminToken, "{\"teamRole\":\"MEMBER\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LAST_LEADER_PROTECTED"));
	}

	@Test
	void should_reject_when_changingRoleToOneTheUserCannotHold() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, new Long[] { manager.getId() });

		send(put("/api/v1/teams/" + id + "/members/" + manager.getId()), adminToken, "{\"teamRole\":\"MEMBER\"}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TEAM_MEMBER"));
	}

	@Test
	void should_returnNotFound_when_removingStranger() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, NONE);

		send(delete("/api/v1/teams/" + id + "/members/" + outsider.getId()), adminToken, null)
				.andExpect(status().isNotFound());
	}

	// ---- sửa / lưu trữ ----

	@Test
	void should_updateTeam_andReturnConflict_when_versionStale() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, NONE);
		long v = version(id);

		send(put("/api/v1/teams/" + id), adminToken, "{\"name\":\"Alpha 2\",\"description\":\"d\",\"version\":" + v + "}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Alpha 2"));

		send(put("/api/v1/teams/" + id), adminToken, "{\"name\":\"Alpha 3\",\"version\":" + v + "}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	@Test
	void should_rejectChanges_when_teamArchived() throws Exception {
		long id = createTeam("Alpha", leaderA, NONE, NONE);
		send(post("/api/v1/teams/" + id + "/archive"), adminToken, "{\"version\":" + version(id) + "}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ARCHIVED"));

		send(post("/api/v1/teams/" + id + "/members"), adminToken,
				"{\"userId\":%d,\"teamRole\":\"MEMBER\"}".formatted(member.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TEAM_ARCHIVED"));
		send(put("/api/v1/teams/" + id), adminToken, "{\"name\":\"X\",\"version\":" + version(id) + "}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TEAM_ARCHIVED"));
	}

	@Test
	void should_returnNotFound_when_teamMissing() throws Exception {
		send(get("/api/v1/teams/999999"), adminToken, null).andExpect(status().isNotFound());
		send(put("/api/v1/teams/999999"), adminToken, "{\"name\":\"X\",\"version\":0}").andExpect(status().isNotFound());
	}

	// ---- nhân sự cho Manager ----

	@Test
	void should_letManagerListUsersButNotEdit() throws Exception {
		String token = accessTokenOf("manager@example.com");

		send(get("/api/v1/users"), token, null).andExpect(status().isOk());
		send(put("/api/v1/users/" + member.getId()), token, "{\"fullName\":\"X\",\"version\":0}")
				.andExpect(status().isForbidden());
		send(get("/api/v1/users"), accessTokenOf("member@example.com"), null).andExpect(status().isForbidden());
	}

	@Test
	void should_returnMinimalOptions_when_listingUserOptions() throws Exception {
		userService.lock(outsider.getId());

		send(get("/api/v1/users/options?role=USER"), adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.items[0].email").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].status").doesNotExist());
		send(get("/api/v1/users/options?role=MANAGER"), accessTokenOf("manager@example.com"), null)
				.andExpect(jsonPath("$.data.items.length()").value(2));
	}

}

package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;

class TaskApiIntegrationTest extends IntegrationTestBase {

	private static final byte[] PDF = "%PDF-1.7 hello world".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] EXE = "MZ\u0090\u0000 not a pdf".getBytes(StandardCharsets.ISO_8859_1);

	private User leaderA;
	private User leaderB;
	private User member;
	private User member2;
	private User manager;
	private String adminToken;
	private String leaderAToken;
	private String leaderBToken;
	private String memberToken;
	private String member2Token;
	private String managerToken;
	private String outsiderToken;
	private long teamA;
	private long teamB;

	@BeforeEach
	void setUp() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		leaderA = createUser("leader-a@example.com", SystemRole.USER);
		leaderB = createUser("leader-b@example.com", SystemRole.USER);
		member = createUser("member@example.com", SystemRole.USER);
		member2 = createUser("member2@example.com", SystemRole.USER);
		manager = createUser("manager@example.com", SystemRole.MANAGER);
		createUser("outsider@example.com", SystemRole.USER);
		adminToken = accessTokenOf("admin@example.com");
		leaderAToken = accessTokenOf("leader-a@example.com");
		leaderBToken = accessTokenOf("leader-b@example.com");
		memberToken = accessTokenOf("member@example.com");
		member2Token = accessTokenOf("member2@example.com");
		managerToken = accessTokenOf("manager@example.com");
		outsiderToken = accessTokenOf("outsider@example.com");

		teamA = createTeam("Alpha", leaderA, new User[] { member, member2 }, new User[] { manager });
		teamB = createTeam("Beta", leaderB, new User[] {}, new User[] {});
	}

	// ---- helpers ----

	private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", bearer(token));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private long createTeam(String name, User leader, User[] members, User[] observers) throws Exception {
		String body = "{\"name\":\"%s\",\"leaderIds\":[%d],\"memberIds\":%s,\"observerIds\":%s}"
				.formatted(name, leader.getId(), ids(members), ids(observers));
		String json = send(post("/api/v1/teams"), adminToken, body).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	private static String ids(User[] users) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < users.length; i++) {
			sb.append(i == 0 ? "" : ",").append(users[i].getId());
		}
		return sb.append("]").toString();
	}

	private String taskBody(String title, User assignee, boolean requireFile) {
		return "{\"title\":\"%s\",\"description\":\"d\",\"assigneeId\":%d,\"dueDate\":\"2030-01-31\",\"priority\":\"HIGH\",\"requireFile\":%s}"
				.formatted(title, assignee.getId(), requireFile);
	}

	private long createTask(String title, User assignee, boolean requireFile) throws Exception {
		String json = send(post("/api/v1/teams/" + teamA + "/tasks"), leaderAToken, taskBody(title, assignee, requireFile))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	private long versionOf(long taskId) throws Exception {
		String json = send(get("/api/v1/tasks/" + taskId), leaderAToken, null).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.version")).longValue();
	}

	/** Gọi một hành động đổi trạng thái với version hiện tại. */
	private ResultActions act(long taskId, String action, String token) throws Exception {
		return send(post("/api/v1/tasks/" + taskId + "/" + action), token, "{\"version\":" + versionOf(taskId) + "}");
	}

	private ResultActions upload(long taskId, String token, String name, byte[] content) throws Exception {
		return mockMvc.perform(multipart("/api/v1/tasks/" + taskId + "/submissions")
				.file(new MockMultipartFile("file", name, "application/octet-stream", content))
				.header("Authorization", bearer(token)));
	}

	private long uploadOk(long taskId, String token) throws Exception {
		String json = upload(taskId, token, "report.pdf", PDF).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	private long startedTask(boolean requireFile) throws Exception {
		long id = createTask("Viết báo cáo", member, requireFile);
		act(id, "start", memberToken).andExpect(status().isOk());
		return id;
	}

	// ---- luồng đầy đủ ----

	@Test
	void should_runFullFlow_assignWorkSubmitApprove() throws Exception {
		long id = createTask("Viết báo cáo", member, true);

		act(id, "start", memberToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
		uploadOk(id, memberToken);
		act(id, "submit-for-review", memberToken).andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
		act(id, "approve", leaderAToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"));

		send(get("/api/v1/tasks/" + id + "/history"), leaderAToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[0].fromStatus").doesNotExist())
				.andExpect(jsonPath("$.data[0].toStatus").value("ASSIGNED"))
				.andExpect(jsonPath("$.data[0].changedBy.id").value(leaderA.getId()))
				.andExpect(jsonPath("$.data[1].toStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.data[1].changedBy.id").value(member.getId()))
				.andExpect(jsonPath("$.data[2].toStatus").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.data[3].fromStatus").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.data[3].toStatus").value("DONE"))
				.andExpect(jsonPath("$.data[3].changedBy.fullName").exists());
	}

	@Test
	void should_returnToInProgressWithReason_when_leaderRequestsRevision() throws Exception {
		long id = startedTask(false);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());

		send(post("/api/v1/tasks/" + id + "/request-revision"), leaderAToken,
				"{\"version\":" + versionOf(id) + ",\"reason\":\"Thiếu số liệu quý 3\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.data.revisionReason").value("Thiếu số liệu quý 3"));

		send(get("/api/v1/tasks/" + id + "/history"), memberToken, null)
				.andExpect(jsonPath("$.data[3].fromStatus").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.data[3].toStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.data[3].note").value("Thiếu số liệu quý 3"));

		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		act(id, "approve", leaderAToken).andExpect(jsonPath("$.data.revisionReason").doesNotExist());
	}

	@Test
	void should_requireReason_when_requestingRevision() throws Exception {
		long id = startedTask(false);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());

		send(post("/api/v1/tasks/" + id + "/request-revision"), leaderAToken,
				"{\"version\":" + versionOf(id) + ",\"reason\":\"  \"}")
				.andExpect(status().isUnprocessableContent());
	}

	// ---- bắt buộc nộp file ----

	@Test
	void should_blockSubmit_when_fileRequiredButMissing() throws Exception {
		long id = startedTask(true);

		act(id, "submit-for-review", memberToken)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("FILE_REQUIRED"));

		uploadOk(id, memberToken);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
	}

	@Test
	void should_allowSubmitWithoutFile_when_fileNotRequired() throws Exception {
		long id = startedTask(false);

		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
	}

	// ---- giao việc ----

	@Test
	void should_onlyLeaderOfTheTeamCreateTasks() throws Exception {
		String body = taskBody("X", member, false);
		String url = "/api/v1/teams/" + teamA + "/tasks";

		send(post(url), memberToken, body).andExpect(status().isForbidden());
		send(post(url), managerToken, body).andExpect(status().isForbidden());
		send(post(url), adminToken, body).andExpect(status().isForbidden());
		send(post(url), outsiderToken, body).andExpect(status().isForbidden());
		send(post(url), leaderBToken, body).andExpect(status().isForbidden());
		send(post(url), leaderAToken, body).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.data.assignee.id").value(member.getId()))
				.andExpect(jsonPath("$.data.assignedBy.id").value(leaderA.getId()))
				.andExpect(jsonPath("$.data.teamName").value("Alpha"));
	}

	@Test
	void should_blockAssigning_when_assigneeIsNotAMemberOfTheTeam() throws Exception {
		String url = "/api/v1/teams/" + teamA + "/tasks";
		User outsider = userRepository.findByEmailIgnoreCase("outsider@example.com").orElseThrow();
		User admin = userRepository.findByEmailIgnoreCase("admin@example.com").orElseThrow();

		for (User bad : new User[] { outsider, leaderB, manager, leaderA, admin }) {
			send(post(url), leaderAToken, taskBody("X", bad, false))
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_IN_TEAM"));
		}
	}

	@Test
	void should_blockCreate_when_teamArchived() throws Exception {
		send(post("/api/v1/teams/" + teamA + "/archive"), adminToken, "{\"version\":" + teamVersion(teamA) + "}")
				.andExpect(status().isOk());

		send(post("/api/v1/teams/" + teamA + "/tasks"), leaderAToken, taskBody("X", member, false))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TEAM_ARCHIVED"));
	}

	private long teamVersion(long id) throws Exception {
		String json = send(get("/api/v1/teams/" + id), adminToken, null).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.version")).longValue();
	}

	@Test
	void should_returnNotFound_when_teamMissing() throws Exception {
		send(post("/api/v1/teams/999999/tasks"), leaderAToken, taskBody("X", member, false))
				.andExpect(status().isNotFound());
	}

	// ---- quyền đổi trạng thái ----

	@Test
	void should_onlyAssigneeStartAndSubmit() throws Exception {
		long id = createTask("T", member, false);

		for (String token : new String[] { member2Token, leaderAToken, managerToken, outsiderToken, adminToken }) {
			act(id, "start", token).andExpect(status().isForbidden());
		}
		act(id, "start", memberToken).andExpect(status().isOk());
		for (String token : new String[] { member2Token, leaderAToken, outsiderToken }) {
			act(id, "submit-for-review", token).andExpect(status().isForbidden());
		}
	}

	@Test
	void should_onlyLeaderOfTheTeamApproveOrReturn() throws Exception {
		long id = startedTask(false);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());

		for (String token : new String[] { memberToken, member2Token, managerToken, outsiderToken, leaderBToken, adminToken }) {
			act(id, "approve", token).andExpect(status().isForbidden());
			send(post("/api/v1/tasks/" + id + "/request-revision"), token,
					"{\"version\":" + versionOf(id) + ",\"reason\":\"x\"}").andExpect(status().isForbidden());
		}
		send(get("/api/v1/tasks/" + id), leaderAToken, null).andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
	}

	@Test
	void should_rejectInvalidTransitions_with409() throws Exception {
		long id = createTask("T", member, false);

		act(id, "submit-for-review", memberToken).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
		act(id, "approve", leaderAToken).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

		act(id, "start", memberToken).andExpect(status().isOk());
		act(id, "start", memberToken).andExpect(status().isConflict());
		act(id, "approve", leaderAToken).andExpect(status().isConflict());

		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		act(id, "submit-for-review", memberToken).andExpect(status().isConflict());
		act(id, "approve", leaderAToken).andExpect(status().isOk());
		act(id, "approve", leaderAToken).andExpect(status().isConflict());
		send(post("/api/v1/tasks/" + id + "/request-revision"), leaderAToken,
				"{\"version\":" + versionOf(id) + ",\"reason\":\"x\"}").andExpect(status().isConflict());
	}

	@Test
	void should_returnConflict_when_versionIsStale() throws Exception {
		long id = createTask("T", member, false);
		long stale = versionOf(id);
		act(id, "start", memberToken).andExpect(status().isOk());

		send(post("/api/v1/tasks/" + id + "/submit-for-review"), memberToken, "{\"version\":" + stale + "}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
		send(put("/api/v1/tasks/" + id), leaderAToken,
				taskBody("Mới", member, false).replace("}", ",\"version\":" + stale + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	@Test
	void should_requireVersion_inActionBody() throws Exception {
		long id = createTask("T", member, false);

		send(post("/api/v1/tasks/" + id + "/start"), memberToken, "{}").andExpect(status().isUnprocessableContent());
	}

	// ---- sửa task ----

	@Test
	void should_ignoreStatusSentInUpdate() throws Exception {
		long id = createTask("T", member, false);
		String body = taskBody("Tiêu đề mới", member2, true).replace("}", ",\"status\":\"DONE\",\"version\":" + versionOf(id) + "}");

		send(put("/api/v1/tasks/" + id), leaderAToken, body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.data.title").value("Tiêu đề mới"))
				.andExpect(jsonPath("$.data.assignee.id").value(member2.getId()))
				.andExpect(jsonPath("$.data.requireFile").value(true));
	}

	@Test
	void should_onlyLeaderUpdate_andNotAfterDone() throws Exception {
		long id = startedTask(false);
		String body = taskBody("Mới", member, false).replace("}", ",\"version\":" + versionOf(id) + "}");

		for (String token : new String[] { memberToken, managerToken, leaderBToken, outsiderToken }) {
			send(put("/api/v1/tasks/" + id), token, body).andExpect(status().isForbidden());
		}
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		act(id, "approve", leaderAToken).andExpect(status().isOk());

		String after = taskBody("Mới", member, false).replace("}", ",\"version\":" + versionOf(id) + "}");
		send(put("/api/v1/tasks/" + id), leaderAToken, after)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
	}

	@Test
	void should_blockReassigning_toNonMember() throws Exception {
		long id = createTask("T", member, false);
		String body = taskBody("T", leaderB, false).replace("}", ",\"version\":" + versionOf(id) + "}");

		send(put("/api/v1/tasks/" + id), leaderAToken, body)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_IN_TEAM"));
	}

	// ---- xem ----

	@Test
	void should_letTeamPeopleAndAdminViewTask_andBlockOthers() throws Exception {
		long id = createTask("T", member, false);

		for (String token : new String[] { leaderAToken, memberToken, member2Token, managerToken, adminToken }) {
			send(get("/api/v1/tasks/" + id), token, null).andExpect(status().isOk());
			send(get("/api/v1/tasks/" + id + "/history"), token, null).andExpect(status().isOk());
		}
		for (String token : new String[] { outsiderToken, leaderBToken }) {
			send(get("/api/v1/tasks/" + id), token, null).andExpect(status().isForbidden());
			send(get("/api/v1/tasks/" + id + "/history"), token, null).andExpect(status().isForbidden());
		}
		send(get("/api/v1/tasks/999999"), leaderAToken, null).andExpect(status().isNotFound());
	}

	@Test
	void should_returnUnauthorized_when_anonymous() throws Exception {
		mockMvc.perform(get("/api/v1/tasks/mine")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/teams/" + teamA + "/tasks")).andExpect(status().isUnauthorized());
	}

	// ---- danh sách ----

	@Test
	void should_listTeamTasks_withFilters() throws Exception {
		long a = createTask("Alpha việc", member, false);
		createTask("Beta việc", member2, false);
		act(a, "start", memberToken).andExpect(status().isOk());
		String url = "/api/v1/teams/" + teamA + "/tasks";

		send(get(url), memberToken, null).andExpect(jsonPath("$.data.totalItems").value(2));
		send(get(url + "?status=IN_PROGRESS"), memberToken, null)
				.andExpect(jsonPath("$.data.totalItems").value(1))
				.andExpect(jsonPath("$.data.items[0].title").value("Alpha việc"));
		send(get(url + "?assigneeId=" + member2.getId()), memberToken, null)
				.andExpect(jsonPath("$.data.items[0].title").value("Beta việc"));
		send(get(url + "?q=beta"), memberToken, null).andExpect(jsonPath("$.data.totalItems").value(1));
		send(get(url + "?dueFrom=2030-02-01"), memberToken, null).andExpect(jsonPath("$.data.totalItems").value(0));
		send(get(url + "?dueTo=2030-01-31"), memberToken, null).andExpect(jsonPath("$.data.totalItems").value(2));
		send(get(url + "?dueFrom=nope"), memberToken, null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_FILTER"));
		send(get(url + "?sort=assignedBy"), memberToken, null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

	@Test
	void should_blockListing_forPeopleOutsideTheTeam() throws Exception {
		send(get("/api/v1/teams/" + teamA + "/tasks"), outsiderToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/teams/" + teamA + "/tasks"), leaderBToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/teams/" + teamA + "/tasks"), adminToken, null).andExpect(status().isOk());
	}

	@Test
	void should_listOnlyMyTasks_acrossTeams() throws Exception {
		createTask("Của member", member, false);
		createTask("Của member2", member2, false);

		send(get("/api/v1/tasks/mine"), memberToken, null)
				.andExpect(jsonPath("$.data.totalItems").value(1))
				.andExpect(jsonPath("$.data.items[0].title").value("Của member"));
		send(get("/api/v1/tasks/mine?assigneeId=" + member2.getId()), memberToken, null)
				.andExpect(jsonPath("$.data.totalItems").value(1))
				.andExpect(jsonPath("$.data.items[0].title").value("Của member"));
		send(get("/api/v1/tasks/mine"), outsiderToken, null).andExpect(jsonPath("$.data.totalItems").value(0));
	}

	// ---- file ----

	@Test
	void should_rejectBadFiles_withClearErrors() throws Exception {
		long id = startedTask(false);

		upload(id, memberToken, "run.exe", EXE).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_FILE"));
		upload(id, memberToken, "fake.pdf", EXE).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_FILE"));
		upload(id, memberToken, "empty.pdf", new byte[0]).andExpect(status().isUnprocessableContent());
		byte[] big = new byte[10 * 1024 * 1024 + 1];
		System.arraycopy(PDF, 0, big, 0, PDF.length);
		upload(id, memberToken, "big.pdf", big).andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
		send(get("/api/v1/tasks/" + id), memberToken, null).andExpect(jsonPath("$.data.submissions.length()").value(0));
	}

	@Test
	void should_onlyAssigneeUpload_andOnlyWhileInProgress() throws Exception {
		long id = createTask("T", member, false);

		upload(id, memberToken, "a.pdf", PDF).andExpect(status().isConflict());
		act(id, "start", memberToken).andExpect(status().isOk());
		for (String token : new String[] { member2Token, leaderAToken, managerToken, outsiderToken }) {
			upload(id, token, "a.pdf", PDF).andExpect(status().isForbidden());
		}
		upload(id, memberToken, "a.pdf", PDF).andExpect(status().isCreated());
		upload(id, memberToken, "b.pdf", PDF).andExpect(status().isCreated());
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		upload(id, memberToken, "c.pdf", PDF).andExpect(status().isConflict());
	}

	@Test
	void should_downloadSameBytes_forAssigneeAndLeaderOnly() throws Exception {
		long id = startedTask(false);
		long sid = uploadOk(id, memberToken);
		String url = "/api/v1/tasks/" + id + "/submissions/" + sid + "/download";

		for (String token : new String[] { memberToken, leaderAToken }) {
			byte[] body = send(get(url), token, null).andExpect(status().isOk())
					.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
					.andExpect(header().string("Content-Type", "application/pdf"))
					.andReturn().getResponse().getContentAsByteArray();
			assertThat(body).isEqualTo(PDF);
		}
		for (String token : new String[] { member2Token, managerToken, outsiderToken, leaderBToken, adminToken }) {
			send(get(url), token, null).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}

	@Test
	void should_returnNotFound_when_submissionBelongsToAnotherTask() throws Exception {
		long id = startedTask(false);
		long other = createTask("Khác", member2, false);
		long sid = uploadOk(id, memberToken);

		send(get("/api/v1/tasks/" + other + "/submissions/" + sid + "/download"), leaderAToken, null)
				.andExpect(status().isNotFound());
		send(get("/api/v1/tasks/" + id + "/submissions/999999/download"), leaderAToken, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void should_listSubmissionsInTaskDetail_withSafeName() throws Exception {
		long id = startedTask(false);

		upload(id, memberToken, "../../evil/báo cáo.pdf", PDF).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.fileName").value("báo cáo.pdf"));

		send(get("/api/v1/tasks/" + id), managerToken, null)
				.andExpect(jsonPath("$.data.submissions.length()").value(1))
				.andExpect(jsonPath("$.data.submissions[0].fileName").value("báo cáo.pdf"))
				.andExpect(jsonPath("$.data.submissions[0].storageKey").doesNotExist());
	}
}

package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskCommentReactedEvent;
import com.soudan.task.TaskCommentedEvent;
import com.soudan.task.TaskReviewedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.user.domain.User;

@RecordApplicationEvents
class TaskApiIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ApplicationEvents events;

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

	// ---- sự kiện để gửi email ----

	@Test
	void should_publishAssignedEvent_when_taskCreated() throws Exception {
		long id = createTask("Viết báo cáo", member, false);

		var published = events.stream(TaskAssignedEvent.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.get(0).taskId()).isEqualTo(id);
		assertThat(published.get(0).assigneeId()).isEqualTo(member.getId());
		assertThat(published.get(0).assignedById()).isEqualTo(leaderA.getId());
		assertThat(published.get(0).title()).isEqualTo("Viết báo cáo");
	}

	@Test
	void should_publishAssignedEvent_onlyWhenAssigneeChanges() throws Exception {
		long id = createTask("T", member, false);
		events.clear();

		send(put("/api/v1/tasks/" + id), leaderAToken,
				taskBody("Đổi tiêu đề", member, false).replace("}", ",\"version\":" + versionOf(id) + "}"))
				.andExpect(status().isOk());
		assertThat(events.stream(TaskAssignedEvent.class).count()).isZero();

		send(put("/api/v1/tasks/" + id), leaderAToken,
				taskBody("Đổi tiêu đề", member2, false).replace("}", ",\"version\":" + versionOf(id) + "}"))
				.andExpect(status().isOk());
		var published = events.stream(TaskAssignedEvent.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.get(0).assigneeId()).isEqualTo(member2.getId());
	}

	@Test
	void should_publishSubmittedEvent_onlyWhenSubmitSucceeds() throws Exception {
		long id = startedTask(true);
		events.clear();

		act(id, "submit-for-review", memberToken).andExpect(status().isUnprocessableContent());
		assertThat(events.stream(TaskSubmittedForReviewEvent.class).count()).isZero();

		uploadOk(id, memberToken);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		var published = events.stream(TaskSubmittedForReviewEvent.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.get(0).taskId()).isEqualTo(id);
		assertThat(published.get(0).teamId()).isEqualTo(teamA);
		assertThat(published.get(0).assigneeId()).isEqualTo(member.getId());
	}

	@Test
	void should_publishReviewedEvent_when_leaderApprovesOrSendsBack() throws Exception {
		long id = startedTask(false);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		events.clear();

		send(post("/api/v1/tasks/" + id + "/request-revision"), leaderAToken,
				"{\"version\":" + versionOf(id) + ",\"reason\":\"Thiếu số liệu\"}").andExpect(status().isOk());
		var back = events.stream(TaskReviewedEvent.class).toList();
		assertThat(back).hasSize(1);
		assertThat(back.get(0).approved()).isFalse();
		assertThat(back.get(0).reason()).isEqualTo("Thiếu số liệu");
		assertThat(back.get(0).assigneeId()).isEqualTo(member.getId());
		assertThat(back.get(0).reviewerId()).isEqualTo(leaderA.getId());

		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		events.clear();
		act(id, "approve", leaderAToken).andExpect(status().isOk());
		var approved = events.stream(TaskReviewedEvent.class).toList();
		assertThat(approved).hasSize(1);
		assertThat(approved.get(0).approved()).isTrue();
	}

	@Test
	void should_publishReactedEvent_onlyForNewReactions() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "xong");
		events.clear();

		react(id, comment, leaderAToken, "👍").andExpect(status().isOk());
		var published = events.stream(TaskCommentReactedEvent.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.get(0).commentAuthorId()).isEqualTo(member.getId());
		assertThat(published.get(0).actorId()).isEqualTo(leaderA.getId());
		assertThat(published.get(0).emoji()).isEqualTo("👍");

		// Thả lại cùng emoji không phát thêm sự kiện
		react(id, comment, leaderAToken, "👍").andExpect(status().isOk());
		assertThat(events.stream(TaskCommentReactedEvent.class).count()).isEqualTo(1);
	}

	@Test
	void should_notPublishAssignedEvent_when_createRejected() throws Exception {
		send(post("/api/v1/teams/" + teamA + "/tasks"), memberToken, taskBody("X", member, false))
				.andExpect(status().isForbidden());
		send(post("/api/v1/teams/" + teamA + "/tasks"), leaderAToken, taskBody("X", leaderB, false))
				.andExpect(status().isUnprocessableContent());

		assertThat(events.stream(TaskAssignedEvent.class).count()).isZero();
	}

	// ---- bình luận ----

	private long commentOk(long taskId, String token, String text) throws Exception {
		String json = send(post("/api/v1/tasks/" + taskId + "/comments"), token, "{\"body\":\"" + text + "\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	@Test
	void should_addAndListComments_inOrder() throws Exception {
		long id = createTask("T", member, false);

		commentOk(id, memberToken, "Em bắt đầu làm");
		send(post("/api/v1/tasks/" + id + "/comments"), leaderAToken, "{\"body\":\"  Ok, nhớ nộp file nhé  \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.author.id").value(leaderA.getId()))
				.andExpect(jsonPath("$.data.body").value("Ok, nhớ nộp file nhé"));

		send(get("/api/v1/tasks/" + id + "/comments"), managerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].body").value("Em bắt đầu làm"))
				.andExpect(jsonPath("$.data[0].author.fullName").exists())
				.andExpect(jsonPath("$.data[1].author.id").value(leaderA.getId()));
	}

	@Test
	void should_letEveryTeamPersonCommentAndRead_butNotOutsiders() throws Exception {
		long id = createTask("T", member, false);

		for (String token : new String[] { leaderAToken, memberToken, member2Token, managerToken, adminToken }) {
			commentOk(id, token, "xin chào");
			send(get("/api/v1/tasks/" + id + "/comments"), token, null).andExpect(status().isOk());
		}
		for (String token : new String[] { outsiderToken, leaderBToken }) {
			send(get("/api/v1/tasks/" + id + "/comments"), token, null).andExpect(status().isForbidden());
			send(post("/api/v1/tasks/" + id + "/comments"), token, "{\"body\":\"x\"}").andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/api/v1/tasks/" + id + "/comments")).andExpect(status().isUnauthorized());
		send(get("/api/v1/tasks/999999/comments"), leaderAToken, null).andExpect(status().isNotFound());
	}

	@Test
	void should_rejectBlankOrTooLongComment() throws Exception {
		long id = createTask("T", member, false);
		String url = "/api/v1/tasks/" + id + "/comments";

		send(post(url), memberToken, "{\"body\":\"   \"}").andExpect(status().isUnprocessableContent());
		send(post(url), memberToken, "{}").andExpect(status().isUnprocessableContent());
		send(post(url), memberToken, "{\"body\":\"" + "a".repeat(2001) + "\"}").andExpect(status().isUnprocessableContent());
		send(post(url), memberToken, "{\"body\":\"" + "a".repeat(2000) + "\"}").andExpect(status().isCreated());
	}

	@Test
	void should_storeCommentAsPlainText_withoutInterpretingMarkup() throws Exception {
		long id = createTask("T", member, false);

		commentOk(id, memberToken, "<script>alert(1)</script>");

		send(get("/api/v1/tasks/" + id + "/comments"), memberToken, null)
				.andExpect(jsonPath("$.data[0].body").value("<script>alert(1)</script>"));
	}

	@Test
	void should_letAuthorOrLeaderDeleteComment_butNotOthers() throws Exception {
		long id = createTask("T", member, false);
		long mine = commentOk(id, memberToken, "của member");
		long theirs = commentOk(id, member2Token, "của member2");

		send(delete("/api/v1/tasks/" + id + "/comments/" + theirs), memberToken, null).andExpect(status().isForbidden());
		send(delete("/api/v1/tasks/" + id + "/comments/" + theirs), managerToken, null).andExpect(status().isForbidden());
		send(delete("/api/v1/tasks/" + id + "/comments/" + theirs), outsiderToken, null).andExpect(status().isForbidden());

		send(delete("/api/v1/tasks/" + id + "/comments/" + mine), memberToken, null).andExpect(status().isNoContent());
		send(delete("/api/v1/tasks/" + id + "/comments/" + theirs), leaderAToken, null).andExpect(status().isNoContent());

		send(get("/api/v1/tasks/" + id + "/comments"), memberToken, null).andExpect(jsonPath("$.data.length()").value(0));
		send(delete("/api/v1/tasks/" + id + "/comments/" + mine), memberToken, null).andExpect(status().isNotFound());
	}

	@Test
	void should_notDeleteCommentOfAnotherTask() throws Exception {
		long a = createTask("A", member, false);
		long b = createTask("B", member2, false);
		long comment = commentOk(a, memberToken, "x");

		send(delete("/api/v1/tasks/" + b + "/comments/" + comment), leaderAToken, null).andExpect(status().isNotFound());
	}

	// ---- trả lời, sửa, biểu cảm ----

	private long replyOk(long taskId, String token, long replyTo, String text) throws Exception {
		String json = send(post("/api/v1/tasks/" + taskId + "/comments"), token,
				"{\"body\":\"" + text + "\",\"replyToId\":" + replyTo + "}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	@Test
	void should_replyToSomeoneElsesComment_asOneLevelThread() throws Exception {
		long id = createTask("T", member, false);
		long root = commentOk(id, memberToken, "Em có câu hỏi");
		long reply = replyOk(id, leaderAToken, root, "Hỏi đi em");
		// Trả lời một trả lời: vẫn gắn vào bình luận gốc (chỉ một cấp)
		long replyToReply = replyOk(id, memberToken, reply, "Dạ về hạn chót");

		send(get("/api/v1/tasks/" + id + "/comments"), memberToken, null)
				.andExpect(jsonPath("$.data.length()").value(3))
				.andExpect(jsonPath("$.data[0].parentId").doesNotExist())
				.andExpect(jsonPath("$.data[1].parentId").value(root))
				.andExpect(jsonPath("$.data[2].id").value(replyToReply))
				.andExpect(jsonPath("$.data[2].parentId").value(root));
	}

	@Test
	void should_rejectReply_toMissingOrForeignComment() throws Exception {
		long a = createTask("A", member, false);
		long b = createTask("B", member2, false);
		long commentOnA = commentOk(a, memberToken, "x");

		send(post("/api/v1/tasks/" + b + "/comments"), member2Token, "{\"body\":\"y\",\"replyToId\":" + commentOnA + "}")
				.andExpect(status().isNotFound());
		send(post("/api/v1/tasks/" + a + "/comments"), memberToken, "{\"body\":\"y\",\"replyToId\":999999}")
				.andExpect(status().isNotFound());
	}

	@Test
	void should_keepDeletedParentAsPlaceholder_whenItStillHasReplies() throws Exception {
		long id = createTask("T", member, false);
		long root = commentOk(id, memberToken, "bí mật");
		replyOk(id, leaderAToken, root, "trả lời");

		send(delete("/api/v1/tasks/" + id + "/comments/" + root), memberToken, null).andExpect(status().isNoContent());

		send(get("/api/v1/tasks/" + id + "/comments"), leaderAToken, null)
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].deleted").value(true))
				.andExpect(jsonPath("$.data[0].body").doesNotExist())
				.andExpect(jsonPath("$.data[0].author").doesNotExist())
				.andExpect(jsonPath("$.data[1].body").value("trả lời"));

		// Không trả lời được bình luận đã xoá, và không xoá lần hai
		send(post("/api/v1/tasks/" + id + "/comments"), memberToken, "{\"body\":\"z\",\"replyToId\":" + root + "}")
				.andExpect(status().isNotFound());
		send(delete("/api/v1/tasks/" + id + "/comments/" + root), memberToken, null).andExpect(status().isNotFound());
	}

	@Test
	void should_hideDeletedParent_whenNoRepliesRemain() throws Exception {
		long id = createTask("T", member, false);
		long root = commentOk(id, memberToken, "x");
		long reply = replyOk(id, leaderAToken, root, "y");
		send(delete("/api/v1/tasks/" + id + "/comments/" + root), memberToken, null).andExpect(status().isNoContent());
		send(delete("/api/v1/tasks/" + id + "/comments/" + reply), leaderAToken, null).andExpect(status().isNoContent());

		send(get("/api/v1/tasks/" + id + "/comments"), leaderAToken, null).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void should_letOnlyAuthorEdit_andMarkAsEdited() throws Exception {
		long id = createTask("T", member, false);
		long mine = commentOk(id, memberToken, "bản đầu");

		// Leader cũng không được sửa lời người khác
		send(put("/api/v1/tasks/" + id + "/comments/" + mine), leaderAToken, "{\"body\":\"hack\"}").andExpect(status().isForbidden());
		send(put("/api/v1/tasks/" + id + "/comments/" + mine), outsiderToken, "{\"body\":\"hack\"}").andExpect(status().isForbidden());

		send(put("/api/v1/tasks/" + id + "/comments/" + mine), memberToken, "{\"body\":\"  bản sửa  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("bản sửa"))
				.andExpect(jsonPath("$.data.editedAt").exists());

		send(put("/api/v1/tasks/" + id + "/comments/" + mine), memberToken, "{\"body\":\"   \"}")
				.andExpect(status().isUnprocessableContent());
		send(put("/api/v1/tasks/" + id + "/comments/" + mine), memberToken, "{\"body\":\"" + "a".repeat(2001) + "\"}")
				.andExpect(status().isUnprocessableContent());
		send(put("/api/v1/tasks/" + id + "/comments/999999"), memberToken, "{\"body\":\"x\"}").andExpect(status().isNotFound());
	}

	@Test
	void should_notMarkEdited_whenTextUnchanged() throws Exception {
		long id = createTask("T", member, false);
		long mine = commentOk(id, memberToken, "giữ nguyên");

		send(put("/api/v1/tasks/" + id + "/comments/" + mine), memberToken, "{\"body\":\"giữ nguyên\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.editedAt").doesNotExist());
	}

	private ResultActions react(long taskId, long commentId, String token, String emoji) throws Exception {
		return send(put("/api/v1/tasks/" + taskId + "/comments/" + commentId + "/reactions"), token,
				"{\"emoji\":\"" + emoji + "\"}");
	}

	@Test
	void should_countReactions_perEmoji_andMarkMine() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "xong");

		react(id, comment, leaderAToken, "👍").andExpect(status().isOk());
		react(id, comment, member2Token, "👍").andExpect(status().isOk());
		react(id, comment, member2Token, "🎉")
				.andExpect(jsonPath("$.data.reactions.length()").value(2))
				.andExpect(jsonPath("$.data.reactions[0].emoji").value("👍"))
				.andExpect(jsonPath("$.data.reactions[0].count").value(2))
				.andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true))
				.andExpect(jsonPath("$.data.reactions[1].emoji").value("🎉"))
				.andExpect(jsonPath("$.data.reactions[1].count").value(1));

		// Người khác nhìn: tên người đã thả, và "của tôi" tính theo người xem
		send(get("/api/v1/tasks/" + id + "/comments"), managerToken, null)
				.andExpect(jsonPath("$.data[0].reactions[0].reactedByMe").value(false))
				.andExpect(jsonPath("$.data[0].reactions[0].users.length()").value(2));
	}

	@Test
	void should_beIdempotent_whenReactingTwiceWithSameEmoji() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "x");

		react(id, comment, leaderAToken, "👍").andExpect(status().isOk());
		react(id, comment, leaderAToken, "👍")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions[0].count").value(1));
	}

	@Test
	void should_removeOnlyMyOwnReaction() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, member2Token, "x");
		react(id, comment, memberToken, "👍");
		react(id, comment, leaderAToken, "👍");

		send(delete("/api/v1/tasks/" + id + "/comments/" + comment + "/reactions?emoji=👍"), memberToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions[0].count").value(1))
				.andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(false));
		send(delete("/api/v1/tasks/" + id + "/comments/" + comment + "/reactions?emoji=👍"), memberToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions[0].count").value(1));
		send(delete("/api/v1/tasks/" + id + "/comments/" + comment + "/reactions?emoji=👍"), leaderAToken, null)
				.andExpect(jsonPath("$.data.reactions.length()").value(0));
	}

	@Test
	void should_rejectNonEmojiReactions_andOutsiders() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "x");

		for (String bad : new String[] { "a", "<b>", "👍a", "1" }) {
			react(id, comment, leaderAToken, bad).andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.code").value("INVALID_EMOJI"));
		}
		react(id, comment, outsiderToken, "👍").andExpect(status().isForbidden());
		react(id, 999999, memberToken, "👍").andExpect(status().isNotFound());
	}

	@Test
	void should_limitDistinctEmojiPerComment() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "x");
		String[] twentyOne = { "😀", "😁", "😂", "😃", "😄", "😅", "😆", "😇", "😈", "😉", "😊", "😋", "😌", "😍", "😎", "😏", "😐", "😑", "😒", "😓", "😔" };

		for (int i = 0; i < 20; i++) {
			react(id, comment, leaderAToken, twentyOne[i]).andExpect(status().isOk());
		}
		react(id, comment, leaderAToken, twentyOne[20]).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REACTIONS"));
		// Thêm người vào một loại đã có thì vẫn được
		react(id, comment, member2Token, twentyOne[0]).andExpect(status().isOk());
	}

	@Test
	void should_forbidReactingToOwnComment_forEveryone() throws Exception {
		long id = createTask("T", member, false);
		long byMember = commentOk(id, memberToken, "của member");
		long byLeader = commentOk(id, leaderAToken, "của leader");

		react(id, byMember, memberToken, "👍").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SELF_REACTION"));
		react(id, byLeader, leaderAToken, "❤️").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SELF_REACTION"));
		// Thả vào bình luận của người khác thì được; bình luận của mình vẫn không có biểu cảm nào
		react(id, byMember, leaderAToken, "👍").andExpect(status().isOk());
		send(get("/api/v1/tasks/" + id + "/comments"), memberToken, null)
				.andExpect(jsonPath("$.data[1].reactions.length()").value(0));
	}

	@Test
	void should_allowRemovingReactionOnOwnComment_withoutError() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "x");

		send(delete("/api/v1/tasks/" + id + "/comments/" + comment + "/reactions?emoji=👍"), memberToken, null)
				.andExpect(status().isOk());
	}

	@Test
	void should_notReactToDeletedComment() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "x");
		send(delete("/api/v1/tasks/" + id + "/comments/" + comment), memberToken, null).andExpect(status().isNoContent());

		react(id, comment, leaderAToken, "👍").andExpect(status().isNotFound());
	}

	// ---- sự kiện bình luận để gửi email ----

	@Test
	void should_publishCommentedEvent_withReplyTarget() throws Exception {
		long id = createTask("Viết báo cáo", member, false);
		long root = commentOk(id, memberToken, "gốc");
		events.clear();

		long reply = replyOk(id, leaderAToken, root, "trả lời");

		var published = events.stream(TaskCommentedEvent.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.get(0).commentId()).isEqualTo(reply);
		assertThat(published.get(0).authorId()).isEqualTo(leaderA.getId());
		assertThat(published.get(0).assigneeId()).isEqualTo(member.getId());
		assertThat(published.get(0).repliedToAuthorId()).isEqualTo(member.getId());
		assertThat(published.get(0).teamId()).isEqualTo(teamA);
		assertThat(published.get(0).title()).isEqualTo("Viết báo cáo");
		assertThat(published.get(0).body()).isEqualTo("trả lời");
	}

	@Test
	void should_notPublishCommentedEvent_forEditReactionDeleteOrRejectedComment() throws Exception {
		long id = createTask("T", member, false);
		long comment = commentOk(id, memberToken, "x");
		events.clear();

		send(put("/api/v1/tasks/" + id + "/comments/" + comment), memberToken, "{\"body\":\"y\"}").andExpect(status().isOk());
		react(id, comment, leaderAToken, "👍").andExpect(status().isOk());
		send(post("/api/v1/tasks/" + id + "/comments"), outsiderToken, "{\"body\":\"z\"}").andExpect(status().isForbidden());
		send(post("/api/v1/tasks/" + id + "/comments"), memberToken, "{\"body\":\" \"}").andExpect(status().isUnprocessableContent());
		send(delete("/api/v1/tasks/" + id + "/comments/" + comment), memberToken, null).andExpect(status().isNoContent());

		assertThat(events.stream(TaskCommentedEvent.class).count()).isZero();
	}

	@Test
	void should_keepCommentingAllowed_afterTaskDone() throws Exception {
		long id = startedTask(false);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		act(id, "approve", leaderAToken).andExpect(status().isOk());

		commentOk(id, memberToken, "cảm ơn anh");
	}

	// ---- nghiệp vụ bổ sung: hạn chót, chuyển giao, gỡ người còn việc, người bị khoá ----

	@Test
	void should_rejectPastDueDate_when_creating() throws Exception {
		String body = taskBody("Trễ", member, false).replace("2030-01-31", "2020-01-31");

		send(post("/api/v1/teams/" + teamA + "/tasks"), leaderAToken, body)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_DUE_DATE"));
	}

	@Test
	void should_resetToAssignedAndLogHandover_when_reassigned() throws Exception {
		long id = startedTask(false);
		String body = taskBody("Viết báo cáo", member2, false).replace("}", ",\"version\":" + versionOf(id) + "}");

		send(put("/api/v1/tasks/" + id), leaderAToken, body).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignee.id").value(member2.getId()))
				.andExpect(jsonPath("$.data.status").value("ASSIGNED"));

		send(get("/api/v1/tasks/" + id + "/history"), leaderAToken, null)
				.andExpect(jsonPath("$.data[2].toStatus").value("ASSIGNED"))
				.andExpect(jsonPath("$.data[2].note").value(org.hamcrest.Matchers.containsString("Chuyển giao")));
		// Người mới bắt đầu được, người cũ thì không
		act(id, "start", member2Token).andExpect(status().isOk());
	}

	@Test
	void should_blockRemovingMemberWithOpenTasks_until_handedOverOrDone() throws Exception {
		long id = createTask("Việc dở", member, false);
		String url = "/api/v1/teams/" + teamA + "/members/" + member.getId();

		send(delete(url), adminToken, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MEMBER_HAS_OPEN_TASKS"));

		// Chuyển giao cho người khác thì gỡ được
		String body = taskBody("Việc dở", member2, false).replace("}", ",\"version\":" + versionOf(id) + "}");
		send(put("/api/v1/tasks/" + id), leaderAToken, body).andExpect(status().isOk());
		send(delete(url), adminToken, null).andExpect(status().isOk());
	}

	@Test
	void should_allowRemovingMember_when_allTheirTasksAreDone() throws Exception {
		long id = startedTask(false);
		act(id, "submit-for-review", memberToken).andExpect(status().isOk());
		act(id, "approve", leaderAToken).andExpect(status().isOk());

		send(delete("/api/v1/teams/" + teamA + "/members/" + member.getId()), adminToken, null)
				.andExpect(status().isOk());
	}

	@Test
	void should_flagLockedAssignee_andRefuseNewAssignmentsToThem() throws Exception {
		long id = createTask("Giao cho người sắp bị khoá", member, false);
		send(post("/api/v1/users/" + member.getId() + "/lock"), adminToken, null).andExpect(status().isOk());

		send(get("/api/v1/tasks/" + id), leaderAToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignee.active").value(false));
		// Không giao thêm việc cho người bị khoá
		send(post("/api/v1/teams/" + teamA + "/tasks"), leaderAToken, taskBody("Mới", member, false))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ASSIGNEE_INACTIVE"));
		// Nhưng chuyển giao việc cũ sang người khác thì được
		String body = taskBody("Giao cho người sắp bị khoá", member2, false).replace("}", ",\"version\":" + versionOf(id) + "}");
		send(put("/api/v1/tasks/" + id), leaderAToken, body).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignee.active").value(true));
	}
}

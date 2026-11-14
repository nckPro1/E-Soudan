package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;
import com.soudan.notification.service.ReminderService;
import com.soudan.task.domain.Task;
import com.soudan.task.domain.TaskPriority;
import com.soudan.task.repository.TaskRepository;
import com.soudan.user.domain.User;

/**
 * Nhắc việc, nhắc nộp báo cáo, báo nghỉ và danh sách "chưa nộp": ngày nghỉ và cuối tuần không bị tính là thiếu, người bị
 * khoá không bị nhắc, mỗi nhắc chỉ gửi một lần mỗi ngày. Ngày được truyền vào dịch vụ nên kết quả không phụ thuộc giờ chạy test.
 */
class ReminderAndLeaveIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ReminderService reminders;
	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private ZoneId zone;

	private User ann;
	private User bob;
	private User carl;
	private String adminToken;
	private String managerToken;
	private String annToken;
	private String bobToken;
	private String carlToken;
	private long teamId;
	private long communityId;

	@BeforeEach
	void setUp() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		createUser("manager@example.com", SystemRole.MANAGER);
		ann = createUser("ann@example.com", SystemRole.USER);
		bob = createUser("bob@example.com", SystemRole.USER);
		carl = createUser("carl@example.com", SystemRole.USER);
		adminToken = accessTokenOf("admin@example.com");
		managerToken = accessTokenOf("manager@example.com");
		annToken = accessTokenOf("ann@example.com");
		bobToken = accessTokenOf("bob@example.com");
		carlToken = accessTokenOf("carl@example.com");

		String team = "{\"name\":\"Alpha\",\"leaderIds\":[%d],\"memberIds\":[%d,%d]}".formatted(ann.getId(), bob.getId(),
				carl.getId());
		teamId = id(send(post("/api/v1/teams"), adminToken, team).andExpect(status().isCreated()), "$.data.id");
		communityId = id(send(post("/api/v1/communities"), managerToken, "{\"name\":\"Phòng dev\"}"), "$.data.community.id");
		send(post("/api/v1/communities/" + communityId + "/members"), managerToken,
				"{\"userIds\":[%d,%d]}".formatted(bob.getId(), carl.getId())).andExpect(status().isOk());
	}

	// ---- helpers ----

	private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", bearer(token));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private static long id(ResultActions result, String path) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
	}

	private LocalDate today() {
		return reminders.today();
	}

	/** Ngày làm việc (T2–T6) gần nhất từ hôm nay trở đi. */
	private LocalDate nextWorkday() {
		LocalDate d = today();
		while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
			d = d.plusDays(1);
		}
		return d;
	}

	private LocalDate nextSaturday() {
		LocalDate d = today().plusDays(1);
		while (d.getDayOfWeek() != DayOfWeek.SATURDAY) {
			d = d.plusDays(1);
		}
		return d;
	}

	private List<String> notificationTypes(String token) throws Exception {
		String json = send(get("/api/v1/notifications"), token, null).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(json, "$.data.items[*].type");
	}

	private Task saveTask(User assignee, LocalDate due) {
		return taskRepository.saveAndFlush(
				Task.create(teamId, "Việc có hạn", null, assignee.getId(), ann.getId(), due, TaskPriority.MEDIUM, false));
	}

	// ---- nhắc hạn việc ----

	@Test
	void should_remindAssignee_when_taskDueTodayOrTomorrow_andOnlyOncePerDay() throws Exception {
		saveTask(bob, today().plusDays(1));

		assertThat(reminders.remindTasks(today())).isEqualTo(1);

		assertThat(notificationTypes(bobToken)).contains("TASK_DUE_SOON");
		assertThat(notificationTypes(carlToken)).doesNotContain("TASK_DUE_SOON");
		// Chạy lại trong cùng ngày không gửi trùng
		assertThat(reminders.remindTasks(today())).isZero();
	}

	@Test
	void should_notifyAssigneeAndLeaders_when_taskOverdue() throws Exception {
		saveTask(bob, today().minusDays(1));

		assertThat(reminders.remindTasks(today())).isEqualTo(2);

		assertThat(notificationTypes(bobToken)).contains("TASK_OVERDUE");
		assertThat(notificationTypes(annToken)).contains("TASK_OVERDUE");
	}

	@Test
	void should_notRemind_when_taskDoneOrFarAwayOrAssigneeLocked() throws Exception {
		saveTask(bob, today().plusDays(10)); // còn xa
		Task done = saveTask(carl, today());
		// Task DONE: đi đúng luồng duyệt thì phức tạp, nên dùng domain để đánh dấu
		done.start();
		done.submitForReview(true);
		done.approve();
		taskRepository.saveAndFlush(done);
		assertThat(reminders.remindTasks(today())).isZero();

		// Người phụ trách bị khoá thì không nhắc
		saveTask(bob, today());
		send(post("/api/v1/users/" + bob.getId() + "/lock"), adminToken, null).andExpect(status().isOk());
		assertThat(reminders.remindTasks(today())).isZero();
	}

	// ---- nhắc báo cáo và danh sách chưa nộp ----

	@Test
	void should_remindMembersWhoHaveNotSubmitted_onWorkdays_andSkipLeaveAndLocked() throws Exception {
		LocalDate day = nextWorkday();
		send(put("/api/v1/report-days-off/" + day), carlToken, "{\"reason\":\"Khám bệnh\"}").andExpect(status().isOk());

		assertThat(reminders.remindReports(day)).isEqualTo(1); // chỉ Bob; Carl đang nghỉ
		assertThat(notificationTypes(bobToken)).contains("REPORT_REMINDER");
		assertThat(notificationTypes(carlToken)).doesNotContain("REPORT_REMINDER");
		assertThat(reminders.remindReports(day)).isZero(); // không gửi trùng

		send(get("/api/v1/communities/" + communityId + "/pending?date=" + day), managerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.workingDay").value(true))
				.andExpect(jsonPath("$.data.missing.length()").value(1))
				.andExpect(jsonPath("$.data.missing[0].id").value(bob.getId()))
				.andExpect(jsonPath("$.data.onLeave.length()").value(1))
				.andExpect(jsonPath("$.data.onLeave[0].id").value(carl.getId()));

		// Người bị khoá không còn bị tính là thiếu
		send(post("/api/v1/users/" + bob.getId() + "/lock"), adminToken, null).andExpect(status().isOk());
		send(get("/api/v1/communities/" + communityId + "/pending?date=" + day), managerToken, null)
				.andExpect(jsonPath("$.data.missing.length()").value(0));
	}

	@Test
	void should_notCountAnyoneMissing_onWeekend() throws Exception {
		LocalDate saturday = nextSaturday();

		assertThat(reminders.remindReports(saturday)).isZero();
		send(get("/api/v1/communities/" + communityId + "/pending?date=" + saturday), managerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.workingDay").value(false))
				.andExpect(jsonPath("$.data.missing.length()").value(0));
	}

	// ---- báo nghỉ ----

	@Test
	void should_markListAndRemoveOwnDaysOff() throws Exception {
		LocalDate day = today().plusDays(3);
		send(put("/api/v1/report-days-off/" + day), bobToken, "{\"reason\":\"Về quê\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reason").value("Về quê"));
		// Báo lại chỉ cập nhật lý do
		send(put("/api/v1/report-days-off/" + day), bobToken, "{\"reason\":\"Việc nhà\"}").andExpect(status().isOk());

		send(get("/api/v1/report-days-off?from=" + today() + "&to=" + today().plusDays(10)), bobToken, null)
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].reason").value("Việc nhà"));
		// Lịch nghỉ của người khác không lẫn vào
		send(get("/api/v1/report-days-off?from=" + today() + "&to=" + today().plusDays(10)), carlToken, null)
				.andExpect(jsonPath("$.data.length()").value(0));

		send(delete("/api/v1/report-days-off/" + day), bobToken, null).andExpect(status().isOk());
		send(delete("/api/v1/report-days-off/" + day), bobToken, null).andExpect(status().isNotFound());
	}

	@Test
	void should_rejectDaysOffInPastOrTooFar_andForNonMembers() throws Exception {
		send(put("/api/v1/report-days-off/" + today().minusDays(1)), bobToken, null).andExpect(status().isUnprocessableContent());
		send(put("/api/v1/report-days-off/" + today().plusDays(500)), bobToken, null).andExpect(status().isUnprocessableContent());
		send(put("/api/v1/report-days-off/" + today().plusDays(1)), managerToken, null).andExpect(status().isForbidden());
		send(put("/api/v1/report-days-off/" + today().plusDays(1)), adminToken, null).andExpect(status().isForbidden());
	}
}

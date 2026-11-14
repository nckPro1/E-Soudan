package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.demo.DemoDataService;

/**
 * Tài khoản demo: dữ liệu mẫu phải dựng được trên đúng cấu trúc bảng hiện tại (test này bắt lỗi khi schema đổi), đăng nhập
 * được bằng mật khẩu công khai, có đủ dữ liệu của mọi tính năng, làm mới lặp lại không lỗi, và không đổi mật khẩu được.
 * Dữ liệu được tạo trong giao dịch của test nên bị huỷ khi xong, không ảnh hưởng test khác.
 */
@TestPropertySource(properties = { "app.demo.enabled=true", "app.demo.seed-on-startup=false" })
class DemoAccountIntegrationTest extends IntegrationTestBase {

	private static final String EMAIL = "demo@soudan.local";
	private static final String PASSWORD = "12345678";

	@Autowired
	private DemoDataService demo;

	private String token;

	@BeforeEach
	void setUp() throws Exception {
		demo.reseed();
		String json = login(EMAIL, PASSWORD);
		token = JsonPath.read(json, "$.data.accessToken");
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		request.header("Authorization", bearer(token));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	/** Id đầu tiên trong kết quả của một biểu thức lọc JsonPath (luôn trả về mảng). */
	private static long idOf(String json, String path) {
		List<Number> found = JsonPath.read(json, path);
		return found.get(0).longValue();
	}

	private String json(ResultActions result) throws Exception {
		return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	@Test
	void should_publishAccountOnlyWhileDemoEnabled() throws Exception {
		mockMvc.perform(get("/api/v1/demo/account")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(true))
				.andExpect(jsonPath("$.data.email").value(EMAIL))
				.andExpect(jsonPath("$.data.password").value(PASSWORD));
	}

	@Test
	void should_loginAsMemberWithoutForcedPasswordChange() throws Exception {
		String login = login(EMAIL, PASSWORD);

		assertThat((Boolean) JsonPath.read(login, "$.data.mustChangePassword")).isFalse();
		send(get("/api/v1/me"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.systemRole").value("USER"));
	}

	@Test
	void should_haveTasksInEveryStatus_includingOverdueAndReturnedForRevision() throws Exception {
		String body = json(send(get("/api/v1/tasks/mine?size=50"), null));

		List<String> statuses = JsonPath.read(body, "$.data.items[*].status");
		assertThat(Set.copyOf(statuses)).containsExactlyInAnyOrder("ASSIGNED", "IN_PROGRESS", "PENDING_REVIEW", "DONE");
		List<String> due = JsonPath.read(body, "$.data.items[?(@.status != 'DONE')].dueDate");
		assertThat(due.stream().anyMatch(d -> LocalDate.parse(d).isBefore(LocalDate.now().minusDays(0)))).isTrue();
		List<String> revisions = JsonPath.read(body, "$.data.items[?(@.revisionReason)].revisionReason");
		assertThat(revisions).isNotEmpty();
		List<Boolean> requireFile = JsonPath.read(body, "$.data.items[*].requireFile");
		assertThat(requireFile).contains(true);
	}

	@Test
	void should_belongToTeamsAndSeeTeamRoomsWithUnreadMessages() throws Exception {
		String teams = json(send(get("/api/v1/teams?size=20&status=ACTIVE"), null));
		List<String> names = JsonPath.read(teams, "$.data.items[*].name");
		assertThat(names).hasSize(2);
		long teamId = idOf(teams, "$.data.items[?(@.name =~ /.*Cổng thanh toán.*/)].id");

		String room = json(send(get("/api/v1/chat/teams/" + teamId + "/conversation"), null));
		long roomId = ((Number) JsonPath.read(room, "$.data.id")).longValue();
		String messages = json(send(get("/api/v1/chat/conversations/" + roomId + "/messages?size=50"), null));
		assertThat((List<?>) JsonPath.read(messages, "$.data.items")).hasSizeGreaterThan(8);
		send(get("/api/v1/chat/unread"), null).andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));

		// Nhóm đã lưu trữ vẫn xem được lịch sử nhưng không gửi được
		String archived = json(send(get("/api/v1/teams?size=20&status=ARCHIVED"), null));
		assertThat((List<?>) JsonPath.read(archived, "$.data.items")).hasSize(1);
	}

	@Test
	void should_haveAcceptedDirectChatAndPendingInvitesBothWays() throws Exception {
		String list = json(send(get("/api/v1/chat/conversations"), null));

		List<String> statuses = JsonPath.read(list, "$.data[*].status");
		assertThat(statuses).contains("ACCEPTED", "PENDING");
		List<Boolean> mine = JsonPath.read(list, "$.data[?(@.status == 'PENDING')].requestedByMe");
		assertThat(mine).contains(true, false); // một lời mời gửi đi, một lời mời đến
		List<Boolean> canSend = JsonPath.read(list, "$.data[?(@.status == 'ACCEPTED')].canSend");
		assertThat(canSend).containsOnly(true);
	}

	@Test
	void should_haveCommunitiesWithReportsCommentsAndNoReportTodayForDemoUser() throws Exception {
		String communities = json(send(get("/api/v1/communities"), null));
		assertThat((List<?>) JsonPath.read(communities, "$.data")).hasSize(2);
		long id = idOf(communities, "$.data[?(@.name =~ /.*Phòng phát triển.*/)].id");

		long me = ((Number) JsonPath.read(json(send(get("/api/v1/me"), null)), "$.data.id")).longValue();
		String posts = json(send(get("/api/v1/communities/" + id + "/posts?authorId=" + me + "&size=10"), null));
		assertThat((List<?>) JsonPath.read(posts, "$.data.items")).isNotEmpty();
		// Chưa có báo cáo hôm nay: người xem demo tự thử "Viết báo cáo"
		String today = send(get("/api/v1/communities/" + id + "/posts/mine-today"), null).andReturn().getResponse().getContentAsString();
		assertThat(today).doesNotContain("\"title\"");
	}

	@Test
	void should_haveVariedNotificationsCalendarNotesAndDayOff() throws Exception {
		String notifications = json(send(get("/api/v1/notifications?size=50"), null));
		List<String> types = JsonPath.read(notifications, "$.data.items[*].type");
		assertThat(Set.copyOf(types)).contains("TASK_ASSIGNED", "TASK_REVISION", "TASK_COMMENT", "TASK_DUE_SOON", "REPORT_COMMENT",
				"CHAT_REQUEST");
		send(get("/api/v1/notifications/unread-count"), null).andExpect(jsonPath("$.data.count").value(org.hamcrest.Matchers.greaterThan(2)));

		LocalDate today = LocalDate.now();
		String notes = json(send(get("/api/v1/calendar/notes?from=" + today.minusDays(3) + "&to=" + today.plusDays(20)), null));
		assertThat((List<?>) JsonPath.read(notes, "$.data")).hasSizeGreaterThanOrEqualTo(6);
		String off = json(send(get("/api/v1/report-days-off?from=" + today + "&to=" + today.plusDays(30)), null));
		assertThat((List<?>) JsonPath.read(off, "$.data")).isNotEmpty();
	}

	@Test
	void should_reseedRepeatedlyWithoutDuplicatesOrErrors() throws Exception {
		demo.reseed();
		demo.reseed();

		String tasks = json(send(get("/api/v1/tasks/mine?size=50"), null));
		assertThat((List<?>) JsonPath.read(tasks, "$.data.items")).hasSize(9);
		String teams = json(send(get("/api/v1/teams?size=20&status=ACTIVE"), null));
		assertThat((List<?>) JsonPath.read(teams, "$.data.items")).hasSize(2);
	}

	@Test
	void should_refuseChangingTheSharedPassword() throws Exception {
		send(post("/api/v1/me/change-password"),
				"{\"currentPassword\":\"%s\",\"newPassword\":\"MatKhauMoi!2345\"}".formatted(PASSWORD))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_PROTECTED"));
		// Mật khẩu cũ vẫn dùng được
		assertThat(login(EMAIL, PASSWORD)).contains("accessToken");
	}
}

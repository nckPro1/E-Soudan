package com.soudan.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soudan.IntegrationTestBase;
import com.soudan.common.security.SystemRole;
import com.soudan.chat.ChatRequestAcceptedEvent;
import com.soudan.chat.ChatRequestedEvent;
import com.soudan.notification.repository.NotificationRepository;
import com.soudan.report.ReportCommentedEvent;
import com.soudan.report.ReportPostedEvent;
import com.soudan.report.ReportReactedEvent;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskCommentReactedEvent;
import com.soudan.task.TaskCommentedEvent;
import com.soudan.task.TaskReviewedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.team.TeamApi;
import com.soudan.user.UserApi;
import com.soudan.user.domain.User;

/**
 * Thông báo trong ứng dụng. Các sự kiện được phát thật ở service của task và báo cáo (có test kiểm tra phát đúng);
 * ở đây đưa sự kiện thẳng vào listener rồi kiểm tra kết quả qua API thật với PostgreSQL thật.
 */
class NotificationApiIntegrationTest extends IntegrationTestBase {

	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private UserApi userApi;
	@Autowired
	private TeamApi teamApi;
	@Autowired
	private Clock clock;

	/**
	 * Listener thật nhưng tạo trực tiếp (không qua proxy của Spring): bản trong context chạy bất đồng bộ trên luồng
	 * riêng sau khi commit, còn test này cần chạy đồng bộ trong giao dịch của test (sẽ được hoàn tác).
	 */
	private NotificationListener listener;

	private User owner; // Manager, chủ cộng đồng
	private User author; // chủ báo cáo
	private User other; // thành viên khác
	private User assignee;
	private User leader;
	private String ownerToken;
	private String authorToken;
	private String otherToken;
	private String assigneeToken;
	private String leaderToken;
	private long teamId;

	@BeforeEach
	void setUp() throws Exception {
		listener = new NotificationListener(notificationRepository, userApi, teamApi, clock);
		createUser("admin@example.com", SystemRole.ADMIN);
		owner = createUser("owner@example.com", SystemRole.MANAGER);
		author = createUser("author@example.com", SystemRole.USER);
		other = createUser("other@example.com", SystemRole.USER);
		assignee = createUser("assignee@example.com", SystemRole.USER);
		leader = createUser("leader@example.com", SystemRole.USER);
		ownerToken = accessTokenOf("owner@example.com");
		authorToken = accessTokenOf("author@example.com");
		otherToken = accessTokenOf("other@example.com");
		assigneeToken = accessTokenOf("assignee@example.com");
		leaderToken = accessTokenOf("leader@example.com");

		String admin = accessTokenOf("admin@example.com");
		String body = "{\"name\":\"Alpha\",\"leaderIds\":[%d],\"memberIds\":[%d],\"observerIds\":[]}"
				.formatted(leader.getId(), assignee.getId());
		String json = mockMvc.perform(post("/api/v1/teams").header("Authorization", bearer(admin))
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		teamId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	private ResultActions api(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String token) throws Exception {
		return mockMvc.perform(request.header("Authorization", bearer(token)));
	}

	private long unread(String token) throws Exception {
		String json = api(get("/api/v1/notifications/unread-count"), token).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.count")).longValue();
	}

	private int total(String token) throws Exception {
		String json = api(get("/api/v1/notifications"), token).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(json, "$.data.items.length()");
	}

	// ---- báo cáo ----

	@Test
	void ownerIsToldWhenAMemberPostsAReport() throws Exception {
		listener.on(new ReportPostedEvent(1L, 10L, author.getId(), owner.getId(), "Tiến độ tuần"));
		api(get("/api/v1/notifications"), ownerToken).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("REPORT_POSTED"))
				.andExpect(jsonPath("$.data.items[0].actor.id").value(author.getId()))
				.andExpect(jsonPath("$.data.items[0].subject").value("Tiến độ tuần"))
				.andExpect(jsonPath("$.data.items[0].postAuthorId").value(author.getId()))
				.andExpect(jsonPath("$.data.items[0].read").value(false));
		assertThat(total(authorToken)).isZero();
	}

	@Test
	void commentGoesToThePostAuthorAndReplyGoesToTheRepliedPerson() throws Exception {
		// other bình luận báo cáo của author -> author nhận REPORT_COMMENT
		listener.on(new ReportCommentedEvent(1L, 10L, "Báo cáo", author.getId(), 100L, other.getId(), null, "Làm tốt"));
		api(get("/api/v1/notifications"), authorToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("REPORT_COMMENT"))
				.andExpect(jsonPath("$.data.items[0].snippet").value("Làm tốt"));
		assertThat(total(otherToken)).isZero();

		// author trả lời other -> other nhận REPORT_REPLY, author không nhận thông báo của chính mình
		listener.on(new ReportCommentedEvent(1L, 10L, "Báo cáo", author.getId(), 101L, author.getId(), other.getId(), "Cảm ơn"));
		api(get("/api/v1/notifications"), otherToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("REPORT_REPLY"));
		assertThat(total(authorToken)).isEqualTo(1);
	}

	@Test
	void whenTheRepliedPersonIsAlsoThePostAuthorOnlyOneReplyNotificationIsSent() throws Exception {
		// other trả lời bình luận của author, trên báo cáo của author
		listener.on(new ReportCommentedEvent(1L, 10L, "Báo cáo", author.getId(), 102L, other.getId(), author.getId(), "Ok"));
		api(get("/api/v1/notifications"), authorToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("REPORT_REPLY"));
	}

	@Test
	void reactionsNotifyTheAuthorOfTheReactedThingButNeverTheActor() throws Exception {
		listener.on(new ReportReactedEvent(1L, 10L, "Báo cáo", author.getId(), false, author.getId(), other.getId(), "👍"));
		listener.on(new ReportReactedEvent(1L, 10L, "Báo cáo", author.getId(), true, other.getId(), author.getId(), "🎉"));
		api(get("/api/v1/notifications"), authorToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("REPORT_REACTION"))
				.andExpect(jsonPath("$.data.items[0].snippet").value("👍"));
		api(get("/api/v1/notifications"), otherToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("REPORT_COMMENT_REACTION"));
		// Tự thả biểu cảm cho mình thì không có thông báo
		listener.on(new ReportReactedEvent(1L, 10L, "Báo cáo", author.getId(), false, author.getId(), author.getId(), "👍"));
		assertThat(total(authorToken)).isEqualTo(1);
	}

	// ---- task ----

	@Test
	void taskEventsReachTheRightPeople() throws Exception {
		listener.on(new TaskAssignedEvent(5L, teamId, "Làm báo cáo", assignee.getId(), leader.getId(), null));
		api(get("/api/v1/notifications"), assigneeToken).andExpect(jsonPath("$.data.items[0].type").value("TASK_ASSIGNED"))
				.andExpect(jsonPath("$.data.items[0].taskId").value(5))
				.andExpect(jsonPath("$.data.items[0].actor.id").value(leader.getId()));

		listener.on(new TaskSubmittedForReviewEvent(5L, teamId, "Làm báo cáo", assignee.getId()));
		api(get("/api/v1/notifications"), leaderToken).andExpect(jsonPath("$.data.items[0].type").value("TASK_SUBMITTED"));
		assertThat(total(assigneeToken)).isEqualTo(1);

		listener.on(new TaskReviewedEvent(5L, teamId, "Làm báo cáo", assignee.getId(), leader.getId(), false, "Thiếu số liệu"));
		api(get("/api/v1/notifications"), assigneeToken).andExpect(jsonPath("$.data.items[0].type").value("TASK_REVISION"))
				.andExpect(jsonPath("$.data.items[0].snippet").value("Thiếu số liệu"));
		listener.on(new TaskReviewedEvent(5L, teamId, "Làm báo cáo", assignee.getId(), leader.getId(), true, null));
		api(get("/api/v1/notifications"), assigneeToken).andExpect(jsonPath("$.data.items[0].type").value("TASK_APPROVED"));
	}

	@Test
	void taskCommentNotifiesAssigneeAndLeadersAndRepliesAreSeparate() throws Exception {
		// assignee bình luận -> leader nhận TASK_COMMENT, assignee không nhận của chính mình
		listener.on(new TaskCommentedEvent(5L, teamId, "Làm báo cáo", 1L, assignee.getId(), assignee.getId(), null, "Xong rồi"));
		api(get("/api/v1/notifications"), leaderToken).andExpect(jsonPath("$.data.items[0].type").value("TASK_COMMENT"));
		assertThat(total(assigneeToken)).isZero();

		// leader trả lời assignee -> assignee nhận TASK_REPLY (một thông báo, không thêm TASK_COMMENT)
		listener.on(new TaskCommentedEvent(5L, teamId, "Làm báo cáo", 2L, leader.getId(), assignee.getId(), assignee.getId(), "Duyệt nhé"));
		api(get("/api/v1/notifications"), assigneeToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("TASK_REPLY"));

		listener.on(new TaskCommentReactedEvent(5L, teamId, "Làm báo cáo", 2L, leader.getId(), assignee.getId(), "👍"));
		api(get("/api/v1/notifications"), leaderToken).andExpect(jsonPath("$.data.items[0].type").value("TASK_REACTION"));
	}

	// ---- trò chuyện ----

	@Test
	void chatRequestNotifiesTheRecipientAndAcceptanceNotifiesTheRequester() throws Exception {
		listener.on(new ChatRequestedEvent(7L, author.getId(), other.getId()));
		api(get("/api/v1/notifications"), otherToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("CHAT_REQUEST"))
				.andExpect(jsonPath("$.data.items[0].conversationId").value(7))
				.andExpect(jsonPath("$.data.items[0].actor.id").value(author.getId()));
		assertThat(total(authorToken)).isZero();

		listener.on(new ChatRequestAcceptedEvent(7L, other.getId(), author.getId()));
		api(get("/api/v1/notifications"), authorToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("CHAT_REQUEST_ACCEPTED"))
				.andExpect(jsonPath("$.data.items[0].actor.id").value(other.getId()));
	}

	// ---- hộp thông báo ----

	@Test
	void unreadCountListAndMarkingRead() throws Exception {
		listener.on(new ReportPostedEvent(1L, 10L, author.getId(), owner.getId(), "Một"));
		listener.on(new ReportPostedEvent(1L, 11L, other.getId(), owner.getId(), "Hai"));
		assertThat(unread(ownerToken)).isEqualTo(2);
		// Mới nhất trước
		String json = api(get("/api/v1/notifications"), ownerToken).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].subject").value("Hai"))
				.andExpect(jsonPath("$.data.items[1].subject").value("Một")).andReturn().getResponse()
				.getContentAsString();
		long newest = ((Number) JsonPath.read(json, "$.data.items[0].id")).longValue();

		api(post("/api/v1/notifications/" + newest + "/read"), ownerToken).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.read").value(true));
		assertThat(unread(ownerToken)).isEqualTo(1);
		// Đánh dấu lại không đổi gì
		api(post("/api/v1/notifications/" + newest + "/read"), ownerToken).andExpect(status().isOk());
		assertThat(unread(ownerToken)).isEqualTo(1);
		// Lọc chưa đọc
		api(get("/api/v1/notifications?unread=true"), ownerToken).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].subject").value("Một"));

		api(post("/api/v1/notifications/read-all"), ownerToken).andExpect(jsonPath("$.data.updated").value(1));
		assertThat(unread(ownerToken)).isZero();
		api(get("/api/v1/notifications?unread=true"), ownerToken).andExpect(jsonPath("$.data.items.length()").value(0));
		assertThat(total(ownerToken)).isEqualTo(2);
	}

	@Test
	void everyoneOnlySeesAndTouchesTheirOwnNotifications() throws Exception {
		listener.on(new ReportPostedEvent(1L, 10L, author.getId(), owner.getId(), "Của chủ"));
		String json = api(get("/api/v1/notifications"), ownerToken).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.data.items[0].id")).longValue();

		assertThat(total(authorToken)).isZero();
		assertThat(unread(authorToken)).isZero();
		// Người khác không đánh dấu đã đọc thông báo của chủ được và không biết nó có tồn tại
		api(post("/api/v1/notifications/" + id + "/read"), authorToken).andExpect(status().isNotFound());
		assertThat(unread(ownerToken)).isEqualTo(1);
		// "Đọc tất cả" của người khác không đụng tới thông báo của chủ
		api(post("/api/v1/notifications/read-all"), authorToken).andExpect(jsonPath("$.data.updated").value(0));
		assertThat(unread(ownerToken)).isEqualTo(1);
	}

	@Test
	void requestsWithoutTokenAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
	}
}

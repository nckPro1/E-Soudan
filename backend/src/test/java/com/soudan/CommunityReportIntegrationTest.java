package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;
import com.soudan.report.ReportCommentedEvent;
import com.soudan.report.ReportPostedEvent;
import com.soudan.report.ReportReactedEvent;
import com.soudan.user.domain.User;

/** Cộng đồng báo cáo: Manager tạo và thêm người, thành viên đăng báo cáo lên feed chung, mọi người trong cộng đồng thấy nhau. */
@RecordApplicationEvents
class CommunityReportIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ApplicationEvents events;

	private User leader;
	private User memberA;
	private User memberB;
	private User outsider;
	private User manager;
	private User otherManager;
	private String adminToken;
	private String leaderToken;
	private String memberAToken;
	private String memberBToken;
	private String outsiderToken;
	private String managerToken;
	private String otherManagerToken;
	private long communityId;

	@BeforeEach
	void setUp() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		leader = createUser("leader@example.com", SystemRole.USER);
		memberA = createUser("a@example.com", SystemRole.USER);
		memberB = createUser("b@example.com", SystemRole.USER);
		outsider = createUser("outsider@example.com", SystemRole.USER);
		manager = createUser("manager@example.com", SystemRole.MANAGER);
		otherManager = createUser("manager2@example.com", SystemRole.MANAGER);
		adminToken = accessTokenOf("admin@example.com");
		leaderToken = accessTokenOf("leader@example.com");
		memberAToken = accessTokenOf("a@example.com");
		memberBToken = accessTokenOf("b@example.com");
		outsiderToken = accessTokenOf("outsider@example.com");
		managerToken = accessTokenOf("manager@example.com");
		otherManagerToken = accessTokenOf("manager2@example.com");

		String json = send(post("/api/v1/communities"), managerToken, "{\"name\":\"Phòng phát triển số 2\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		communityId = ((Number) JsonPath.read(json, "$.data.community.id")).longValue();
		send(post("/api/v1/communities/" + communityId + "/members"), managerToken,
				"{\"userIds\":[%d,%d,%d]}".formatted(leader.getId(), memberA.getId(), memberB.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.members.length()").value(3));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", bearer(token));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private static final String TITLE = "Báo cáo ngày 13/11";
	private static final String BODY = "<h2>Việc đã làm</h2><p>Xong màn hình A</p><h2>Vướng mắc</h2><p>Thiếu API</p>";
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
	private static final byte[] PDF = "%PDF-1.7 hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
	private static final byte[] EXE = "MZ\u0090\u0000 not a pdf".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

	/** Nộp báo cáo (multipart: tiêu đề, nội dung, file). */
	private ResultActions postReport(String token, String title, String body, MockMultipartFile... files)
			throws Exception {
		var request = multipart("/api/v1/communities/" + communityId + "/posts");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		if (title != null) {
			request.param("title", title);
		}
		if (body != null) {
			request.param("body", body);
		}
		request.header("Authorization", bearer(token));
		return mockMvc.perform(request);
	}

	private static MockMultipartFile upload(String name, byte[] content) {
		return new MockMultipartFile("files", name, "application/octet-stream", content);
	}

	private long submit(String token) throws Exception {
		String json = postReport(token, TITLE, BODY).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	// ---- cộng đồng ----

	@Test
	void onlyManagerCanCreateCommunity() throws Exception {
		send(post("/api/v1/communities"), leaderToken, "{\"name\":\"X\"}").andExpect(status().isForbidden());
		send(post("/api/v1/communities"), memberAToken, "{\"name\":\"X\"}").andExpect(status().isForbidden());
		send(post("/api/v1/communities"), adminToken, "{\"name\":\"X\"}").andExpect(status().isForbidden());
	}

	@Test
	void membersSeeTheirCommunityAndOutsidersDoNot() throws Exception {
		send(get("/api/v1/communities"), memberAToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1));
		send(get("/api/v1/communities"), outsiderToken, null).andExpect(jsonPath("$.data.length()").value(0));
		send(get("/api/v1/communities"), otherManagerToken, null).andExpect(jsonPath("$.data.length()").value(0));
		send(get("/api/v1/communities"), managerToken, null).andExpect(jsonPath("$.data.length()").value(1));
		send(get("/api/v1/communities/" + communityId), outsiderToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/communities/" + communityId), otherManagerToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/communities/" + communityId), adminToken, null).andExpect(status().isForbidden());
	}

	@Test
	void onlyOwnerManagesMembers() throws Exception {
		String body = "{\"userIds\":[%d]}".formatted(outsider.getId());
		send(post("/api/v1/communities/" + communityId + "/members"), leaderToken, body).andExpect(status().isForbidden());
		send(post("/api/v1/communities/" + communityId + "/members"), otherManagerToken, body).andExpect(status().isForbidden());
		send(post("/api/v1/communities/" + communityId + "/members"), managerToken, body).andExpect(status().isOk());
		send(post("/api/v1/communities/" + communityId + "/members"), managerToken, body).andExpect(status().isConflict());
		send(delete("/api/v1/communities/" + communityId + "/members/" + outsider.getId()), memberAToken, null)
				.andExpect(status().isForbidden());
		send(delete("/api/v1/communities/" + communityId + "/members/" + outsider.getId()), managerToken, null)
				.andExpect(status().isOk());
	}

	@Test
	void cannotAddManagerOrAdminAsMember() throws Exception {
		send(post("/api/v1/communities/" + communityId + "/members"), managerToken,
				"{\"userIds\":[%d]}".formatted(otherManager.getId())).andExpect(status().isUnprocessableContent());
	}

	// ---- báo cáo ----

	@Test
	void memberSubmitsAndEveryoneInTheCommunityReadsIt() throws Exception {
		long postId = submit(memberAToken);
		for (String token : new String[] { memberAToken, memberBToken, leaderToken, managerToken }) {
			send(get("/api/v1/communities/" + communityId + "/posts?authorId=" + memberA.getId()), token, null).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items.length()").value(1))
					.andExpect(jsonPath("$.data.items[0].body").value(org.hamcrest.Matchers.containsString("<h2>Việc đã làm</h2><p>Xong màn hình A</p>")));
			send(get("/api/v1/report-posts/" + postId), token, null).andExpect(status().isOk());
		}
		send(get("/api/v1/communities/" + communityId + "/posts?authorId=" + memberA.getId()), outsiderToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/communities/" + communityId + "/posts?authorId=" + memberA.getId()), otherManagerToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/report-posts/" + postId), outsiderToken, null).andExpect(status().isForbidden());
	}

	@Test
	void reportsAreNotSharedInOneFeedEachMemberHasTheirOwnPage() throws Exception {
		submit(memberAToken);
		postReport(memberBToken, "Báo cáo của B", "<p>Việc của B</p>").andExpect(status().isCreated());
		String base = "/api/v1/communities/" + communityId + "/posts";
		// Trang của A chỉ có báo cáo của A, trang của B chỉ có của B
		send(get(base + "?authorId=" + memberA.getId()), memberBToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].title").value(TITLE))
				.andExpect(jsonPath("$.data.items[0].author.id").value(memberA.getId()));
		send(get(base + "?authorId=" + memberB.getId()), memberAToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].title").value("Báo cáo của B"));
		// Người chưa đăng gì có trang trống
		send(get(base + "?authorId=" + leader.getId()), memberAToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
		// Không có danh sách chung: thiếu authorId là yêu cầu sai
		send(get(base), memberAToken, null).andExpect(status().isBadRequest());
		// Người không phải thành viên (chủ cộng đồng, người ngoài) không có trang báo cáo
		send(get(base + "?authorId=" + outsider.getId()), memberAToken, null).andExpect(status().isNotFound());
		send(get(base + "?authorId=" + manager.getId()), memberAToken, null).andExpect(status().isNotFound());
	}

	@Test
	void memberCanReadOthersReportsButNeverPostOrEditForThem() throws Exception {
		long postId = submit(memberAToken);
		// B đọc được báo cáo của A và bình luận / thả biểu cảm
		send(get("/api/v1/report-posts/" + postId), memberBToken, null).andExpect(status().isOk());
		send(post("/api/v1/report-posts/" + postId + "/comments"), memberBToken, "{\"body\":\"Tốt\"}")
				.andExpect(status().isCreated());
		// Nộp báo cáo luôn là của chính người gọi: B nộp thì ra bài của B, bài của A không đổi
		String json = postReport(memberBToken, "Tiêu đề của B", "<p>nội dung B</p>").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		assertThat((Number) JsonPath.read(json, "$.data.id")).isNotEqualTo(postId);
		send(get("/api/v1/report-posts/" + postId), memberAToken, null).andExpect(jsonPath("$.data.title").value(TITLE))
				.andExpect(jsonPath("$.data.editable").value(true));
		send(get("/api/v1/report-posts/" + postId), memberBToken, null).andExpect(jsonPath("$.data.editable").value(false));
	}

	@Test
	void leaderHasNoSpecialRightOverMemberReports() throws Exception {
		long postId = submit(memberAToken);
		// Leader đọc như thành viên, nhưng không sửa/xoá được gì của người khác
		send(get("/api/v1/communities/" + communityId + "/pending"), leaderToken, null).andExpect(status().isForbidden());
		long commentId = ((Number) JsonPath.read(send(post("/api/v1/report-posts/" + postId + "/comments"), memberBToken,
				"{\"body\":\"Ổn\"}").andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
		send(delete("/api/v1/report-posts/" + postId + "/comments/" + commentId), leaderToken, null)
				.andExpect(status().isForbidden());
		send(delete("/api/v1/report-posts/" + postId + "/comments/" + commentId), managerToken, null)
				.andExpect(status().isNoContent());
	}

	@Test
	void submittingTwiceTheSameDayUpdatesTheSameReport() throws Exception {
		long first = submit(memberAToken);
		postReport(memberAToken, "Tiêu đề mới", "<p>Sửa lại</p>").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(first)).andExpect(jsonPath("$.data.title").value("Tiêu đề mới"))
				.andExpect(jsonPath("$.data.body").value("<p>Sửa lại</p>"));
		send(get("/api/v1/communities/" + communityId + "/posts?authorId=" + memberA.getId()), managerToken, null)
				.andExpect(jsonPath("$.data.items.length()").value(1));
		send(get("/api/v1/communities/" + communityId + "/posts/mine-today"), memberAToken, null)
				.andExpect(jsonPath("$.data.id").value(first));
	}

	@Test
	void ownerAndNonMembersCannotSubmit() throws Exception {
		postReport(managerToken, TITLE, BODY).andExpect(status().isForbidden());
		postReport(outsiderToken, TITLE, BODY).andExpect(status().isForbidden());
		postReport(adminToken, TITLE, BODY).andExpect(status().isForbidden());
	}

	@Test
	void titleIsRequired() throws Exception {
		postReport(memberAToken, "   ", BODY).andExpect(status().isUnprocessableContent());
		postReport(memberAToken, "x".repeat(201), BODY).andExpect(status().isUnprocessableContent());
	}

	@Test
	void needsTextOrAnAttachment() throws Exception {
		postReport(memberAToken, TITLE, "  ").andExpect(status().isUnprocessableContent());
		// Chỉ có thẻ rỗng, không có chữ nào
		postReport(memberAToken, TITLE, "<p></p><h2> </h2>").andExpect(status().isUnprocessableContent());
		// Có file đính kèm thì không cần nội dung chữ
		postReport(memberAToken, TITLE, "", upload("anh.png", PNG)).andExpect(status().isCreated());
	}

	@Test
	void bodyIsSanitizedBeforeSaving() throws Exception {
		String evil = "<p onclick=\"x()\">Xin chào <strong>bạn</strong></p><script>alert(1)</script>"
				+ "<img src=x onerror=alert(1)><a href=\"javascript:alert(1)\">bấm</a>"
				+ "<a href=\"https://example.com\">ok</a>";
		String json = postReport(memberAToken, TITLE, evil).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		String saved = JsonPath.read(json, "$.data.body");
		assertThat(saved).contains("<strong>bạn</strong>").contains("example.com")
				.doesNotContain("<script").doesNotContain("onerror").doesNotContain("onclick")
				.doesNotContain("javascript:").doesNotContain("<img");
	}

	// ---- sự kiện cho thông báo ----

	@Test
	void publishesEventsForTheNotificationModule() throws Exception {
		events.clear();
		long postId = submit(memberAToken);
		var posted = events.stream(ReportPostedEvent.class).toList();
		assertThat(posted).hasSize(1);
		assertThat(posted.get(0).authorId()).isEqualTo(memberA.getId());
		assertThat(posted.get(0).ownerId()).isEqualTo(manager.getId());
		assertThat(posted.get(0).title()).isEqualTo(TITLE);

		// Cập nhật báo cáo đã đăng trong ngày không báo lại cho chủ cộng đồng
		postReport(memberAToken, "Tiêu đề mới", "<p>Sửa</p>").andExpect(status().isCreated());
		assertThat(events.stream(ReportPostedEvent.class).count()).isEqualTo(1);

		// Bình luận và trả lời
		String json = send(post("/api/v1/report-posts/" + postId + "/comments"), memberBToken, "{\"body\":\"Tốt\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long commentId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
		send(post("/api/v1/report-posts/" + postId + "/comments"), memberAToken,
				"{\"body\":\"Cảm ơn\",\"replyToId\":" + commentId + "}").andExpect(status().isCreated());
		var commented = events.stream(ReportCommentedEvent.class).toList();
		assertThat(commented).hasSize(2);
		assertThat(commented.get(0).postAuthorId()).isEqualTo(memberA.getId());
		assertThat(commented.get(0).repliedToAuthorId()).isNull();
		assertThat(commented.get(1).repliedToAuthorId()).isEqualTo(memberB.getId());

		// Biểu cảm: chỉ phát khi là biểu cảm MỚI
		send(put("/api/v1/report-posts/" + postId + "/reactions"), memberBToken, "{\"emoji\":\"👍\"}").andExpect(status().isOk());
		send(put("/api/v1/report-posts/" + postId + "/reactions"), memberBToken, "{\"emoji\":\"👍\"}").andExpect(status().isOk());
		send(put("/api/v1/report-posts/" + postId + "/comments/" + commentId + "/reactions"), memberAToken,
				"{\"emoji\":\"🎉\"}").andExpect(status().isOk());
		var reacted = events.stream(ReportReactedEvent.class).toList();
		assertThat(reacted).hasSize(2);
		assertThat(reacted.get(0).onComment()).isFalse();
		assertThat(reacted.get(0).targetAuthorId()).isEqualTo(memberA.getId());
		assertThat(reacted.get(1).onComment()).isTrue();
		assertThat(reacted.get(1).targetAuthorId()).isEqualTo(memberB.getId());
	}

	// ---- đính kèm ----

	@Test
	void attachmentsAreListedAndEveryoneInTheCommunityCanDownload() throws Exception {
		String json = postReport(memberAToken, TITLE, BODY, upload("anh.png", PNG), upload("bao-cao.pdf", PDF))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.attachments.length()").value(2))
				.andExpect(jsonPath("$.data.attachments[0].image").value(true))
				.andExpect(jsonPath("$.data.attachments[1].image").value(false)).andReturn().getResponse()
				.getContentAsString();
		long postId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
		long pdfId = ((Number) JsonPath.read(json, "$.data.attachments[1].id")).longValue();
		String url = "/api/v1/report-posts/" + postId + "/attachments/" + pdfId + "/download";
		for (String token : new String[] {memberAToken, memberBToken, leaderToken, managerToken}) {
			byte[] bytes = send(get(url), token, null).andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsByteArray();
			assertThat(bytes).isEqualTo(PDF);
		}
		send(get(url), outsiderToken, null).andExpect(status().isForbidden());
		send(get(url), otherManagerToken, null).andExpect(status().isForbidden());
		send(get(url), adminToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/report-posts/" + postId + "/attachments/999999/download"), memberBToken, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void fakeOrUnsupportedFilesAreRejectedAndNothingIsSaved() throws Exception {
		// Đuôi .pdf nhưng nội dung là file thực thi
		postReport(memberAToken, TITLE, BODY, upload("virus.pdf", EXE)).andExpect(status().isUnprocessableContent());
		// Loại file không cho phép
		postReport(memberAToken, TITLE, BODY, upload("tool.exe", EXE)).andExpect(status().isUnprocessableContent());
		send(get("/api/v1/communities/" + communityId + "/posts?authorId=" + memberA.getId()), memberAToken, null)
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void atMostTenAttachmentsPerReport() throws Exception {
		MockMultipartFile[] eleven = new MockMultipartFile[11];
		for (int i = 0; i < eleven.length; i++) {
			eleven[i] = upload("anh" + i + ".png", PNG);
		}
		postReport(memberAToken, TITLE, BODY, eleven).andExpect(status().isUnprocessableContent());
		MockMultipartFile[] ten = java.util.Arrays.copyOf(eleven, 10);
		postReport(memberAToken, TITLE, BODY, ten).andExpect(status().isCreated());
		// Đã đủ 10 thì thêm nữa phải bị từ chối
		postReport(memberAToken, TITLE, BODY, upload("them.png", PNG)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void oldAttachmentsCanBeRemovedWhenEditing() throws Exception {
		String json = postReport(memberAToken, TITLE, BODY, upload("a.png", PNG), upload("b.pdf", PDF))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long first = ((Number) JsonPath.read(json, "$.data.attachments[0].id")).longValue();
		var request = multipart("/api/v1/communities/" + communityId + "/posts").param("title", TITLE)
				.param("body", BODY).param("removeAttachmentIds", String.valueOf(first))
				.header("Authorization", bearer(memberAToken));
		mockMvc.perform(request).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.attachments.length()").value(1))
				.andExpect(jsonPath("$.data.attachments[0].fileName").value("b.pdf"));
	}

	@Test
	void ownerSeesWhoHasNotSubmitted() throws Exception {
		submit(memberAToken);
		send(get("/api/v1/communities/" + communityId + "/pending"), managerToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.missing.length()").value(2));
		send(get("/api/v1/communities/" + communityId + "/pending"), memberBToken, null).andExpect(status().isForbidden());
	}

	@Test
	void requestsWithoutTokenAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/communities")).andExpect(status().isUnauthorized());
	}

	// ---- bình luận và biểu cảm ----

	@Test
	void commentReplyEditAndDeleteRules() throws Exception {
		long postId = submit(memberAToken);
		String url = "/api/v1/report-posts/" + postId + "/comments";
		String json = send(post(url), memberBToken, "{\"body\":\"Làm tốt\"}").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long commentId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
		send(post(url), memberAToken, "{\"body\":\"Cảm ơn\",\"replyToId\":%d}".formatted(commentId))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.parentId").value(commentId));
		send(put(url + "/" + commentId), memberAToken, "{\"body\":\"hack\"}").andExpect(status().isForbidden());
		send(put(url + "/" + commentId), memberBToken, "{\"body\":\"Làm rất tốt\"}").andExpect(status().isOk());
		send(post(url), outsiderToken, "{\"body\":\"hi\"}").andExpect(status().isForbidden());
		send(get(url), memberAToken, null).andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void reactionsAreIdempotentAndNeverOnYourOwnContent() throws Exception {
		long postId = submit(memberAToken);
		String reactions = "/api/v1/report-posts/" + postId + "/reactions";
		send(put(reactions), memberAToken, "{\"emoji\":\"👍\"}").andExpect(status().isForbidden());
		send(put(reactions), memberBToken, "{\"emoji\":\"👍\"}").andExpect(status().isOk());
		send(put(reactions), memberBToken, "{\"emoji\":\"👍\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions[0].count").value(1));
		send(put(reactions), memberBToken, "{\"emoji\":\"abc\"}").andExpect(status().isUnprocessableContent());
		send(put(reactions), outsiderToken, "{\"emoji\":\"👍\"}").andExpect(status().isForbidden());
		send(delete(reactions + "?emoji=👍"), memberBToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions.length()").value(0));
	}
}

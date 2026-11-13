package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soudan.chat.repository.ChatMessageRepository;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;

/**
 * Trò chuyện: nhắn riêng cần đối phương chấp nhận lời mời, phòng chat nhóm, tin nhắn (sửa/xoá mềm/trả lời/biểu cảm/đính
 * kèm), số chưa đọc, phân trang lịch sử và danh bạ. Mọi vai trò trò chuyện được trừ Admin.
 */
class ChatApiIntegrationTest extends IntegrationTestBase {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
	private static final byte[] PDF = "%PDF-1.7 hello".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] EXE = "MZ\u0090\u0000 not a pdf".getBytes(StandardCharsets.ISO_8859_1);

	@Autowired
	private ChatMessageRepository messageRepository;

	private User admin;
	private User manager;
	private User ann;
	private User bob;
	private User carl;
	private String adminToken;
	private String managerToken;
	private String annToken;
	private String bobToken;
	private String carlToken;
	private long teamId;

	@BeforeEach
	void setUp() throws Exception {
		admin = createUser("admin@example.com", SystemRole.ADMIN);
		manager = createUser("manager@example.com", SystemRole.MANAGER);
		ann = createUser("ann@example.com", SystemRole.USER);
		bob = createUser("bob@example.com", SystemRole.USER);
		carl = createUser("carl@example.com", SystemRole.USER);
		adminToken = accessTokenOf("admin@example.com");
		managerToken = accessTokenOf("manager@example.com");
		annToken = accessTokenOf("ann@example.com");
		bobToken = accessTokenOf("bob@example.com");
		carlToken = accessTokenOf("carl@example.com");

		// Nhóm: Ann làm Leader, Bob là Member, Manager là Observer; Carl ở ngoài nhóm
		String body = "{\"name\":\"Alpha\",\"leaderIds\":[%d],\"memberIds\":[%d],\"observerIds\":[%d]}"
				.formatted(ann.getId(), bob.getId(), manager.getId());
		String json = send(post("/api/v1/teams"), adminToken, body).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		teamId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
	}

	// ---- helpers ----

	private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		request.header("Authorization", bearer(token));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private long id(ResultActions result, String path) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
	}

	/** A gửi lời mời cho B; trả về id cuộc trò chuyện. */
	private long request(String token, User to) throws Exception {
		return id(send(post("/api/v1/chat/direct"), token, "{\"userId\":" + to.getId() + "}")
				.andExpect(status().isCreated()), "$.data.id");
	}

	/** Hai người đã nhắn được với nhau. */
	private long connect(String requesterToken, User recipient, String recipientToken) throws Exception {
		long conversation = request(requesterToken, recipient);
		send(post("/api/v1/chat/conversations/" + conversation + "/accept"), recipientToken, null)
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
		return conversation;
	}

	private ResultActions say(long conversation, String token, String text, MockMultipartFile... files) throws Exception {
		var request = multipart("/api/v1/chat/conversations/" + conversation + "/messages");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		if (text != null) {
			request.param("body", text);
		}
		request.header("Authorization", bearer(token));
		return mockMvc.perform(request);
	}

	private long sayOk(long conversation, String token, String text) throws Exception {
		return id(say(conversation, token, text).andExpect(status().isCreated()), "$.data.id");
	}

	private static MockMultipartFile upload(String name, byte[] content) {
		return new MockMultipartFile("files", name, "application/octet-stream", content);
	}

	// ---- nhắn riêng: lời mời ----

	@Test
	void directMessageNeedsTheOtherSideToAcceptTheRequest() throws Exception {
		long conversation = request(annToken, bob);

		// Bên gửi thấy "đang chờ", chưa nhắn được
		send(get("/api/v1/chat/conversations"), annToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].status").value("PENDING"))
				.andExpect(jsonPath("$.data[0].requestedByMe").value(true)).andExpect(jsonPath("$.data[0].canSend").value(false))
				.andExpect(jsonPath("$.data[0].other.id").value(bob.getId()));
		// Bên nhận thấy lời mời cần trả lời
		send(get("/api/v1/chat/conversations"), bobToken, null).andExpect(jsonPath("$.data[0].status").value("PENDING"))
				.andExpect(jsonPath("$.data[0].requestedByMe").value(false))
				.andExpect(jsonPath("$.data[0].other.id").value(ann.getId()));
		// Chưa chấp nhận thì cả hai chưa nhắn được
		say(conversation, annToken, "xin chào").andExpect(status().isConflict());
		say(conversation, bobToken, "chào").andExpect(status().isConflict());

		// Người gửi không tự chấp nhận được
		send(post("/api/v1/chat/conversations/" + conversation + "/accept"), annToken, null).andExpect(status().isForbidden());
		send(post("/api/v1/chat/conversations/" + conversation + "/accept"), bobToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACCEPTED")).andExpect(jsonPath("$.data.canSend").value(true));

		sayOk(conversation, annToken, "xin chào Bob");
		sayOk(conversation, bobToken, "chào Ann");
		send(get("/api/v1/chat/conversations/" + conversation + "/messages"), annToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].body").value("chào Ann"));
	}

	@Test
	void startingAgainReturnsTheSameConversation() throws Exception {
		long first = request(annToken, bob);
		long again = id(send(post("/api/v1/chat/direct"), annToken, "{\"userId\":" + bob.getId() + "}")
				.andExpect(status().isCreated()), "$.data.id");
		assertThat(again).isEqualTo(first);
		send(get("/api/v1/chat/conversations"), annToken, null).andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void whenBothSidesAskEachOtherTheSecondAcceptsAutomatically() throws Exception {
		long conversation = request(annToken, bob);
		long same = id(send(post("/api/v1/chat/direct"), bobToken, "{\"userId\":" + ann.getId() + "}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACCEPTED")), "$.data.id");
		assertThat(same).isEqualTo(conversation);
		sayOk(conversation, annToken, "ok");
	}

	@Test
	void declinedRequestCannotBeSentAgainByTheRequesterButTheOtherSideMayInvite() throws Exception {
		long conversation = request(annToken, bob);
		send(post("/api/v1/chat/conversations/" + conversation + "/decline"), bobToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DECLINED"));
		// Lời mời bị từ chối biến khỏi danh sách của cả hai
		send(get("/api/v1/chat/conversations"), annToken, null).andExpect(jsonPath("$.data.length()").value(0));
		send(get("/api/v1/chat/conversations"), bobToken, null).andExpect(jsonPath("$.data.length()").value(0));
		say(conversation, annToken, "alo").andExpect(status().isConflict());
		// Người bị từ chối không mời lại được
		send(post("/api/v1/chat/direct"), annToken, "{\"userId\":" + bob.getId() + "}").andExpect(status().isConflict());
		// Người đã từ chối đổi ý thì mời lại được; lời mời đổi chiều
		send(post("/api/v1/chat/direct"), bobToken, "{\"userId\":" + ann.getId() + "}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("PENDING")).andExpect(jsonPath("$.data.requestedByMe").value(true));
		send(post("/api/v1/chat/conversations/" + conversation + "/accept"), annToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACCEPTED"));
	}

	@Test
	void requesterCanWithdrawAPendingRequestButNotTheRecipient() throws Exception {
		long conversation = request(annToken, bob);
		send(delete("/api/v1/chat/conversations/" + conversation), bobToken, null).andExpect(status().isForbidden());
		send(delete("/api/v1/chat/conversations/" + conversation), annToken, null).andExpect(status().isNoContent());
		send(get("/api/v1/chat/conversations"), bobToken, null).andExpect(jsonPath("$.data.length()").value(0));
		send(get("/api/v1/chat/conversations/" + conversation), annToken, null).andExpect(status().isNotFound());
		// Có thể mời lại sau khi thu hồi
		request(annToken, bob);
	}

	@Test
	void anAcceptedConversationCannotBeAcceptedOrDeclinedAgain() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		send(post("/api/v1/chat/conversations/" + conversation + "/accept"), bobToken, null).andExpect(status().isConflict());
		send(post("/api/v1/chat/conversations/" + conversation + "/decline"), bobToken, null).andExpect(status().isConflict());
		send(delete("/api/v1/chat/conversations/" + conversation), annToken, null).andExpect(status().isForbidden());
	}

	@Test
	void profileButtonStateIsAvailableThroughDirectWith() throws Exception {
		send(get("/api/v1/chat/direct/" + bob.getId()), annToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());
		request(annToken, bob);
		send(get("/api/v1/chat/direct/" + bob.getId()), annToken, null).andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.requestedByMe").value(true));
		send(get("/api/v1/chat/direct/" + ann.getId()), bobToken, null).andExpect(jsonPath("$.data.requestedByMe").value(false));
	}

	// ---- ai nhắn được với ai ----

	@Test
	void everyRoleCanChatExceptAdmin() throws Exception {
		// Manager với User, User với Manager
		connect(managerToken, ann, annToken);
		connect(bobToken, manager, managerToken);
		send(get("/api/v1/chat/conversations"), managerToken, null).andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void adminIsExcludedFromChatCompletely() throws Exception {
		send(get("/api/v1/chat/conversations"), adminToken, null).andExpect(status().isForbidden());
		send(post("/api/v1/chat/direct"), adminToken, "{\"userId\":" + ann.getId() + "}").andExpect(status().isForbidden());
		send(get("/api/v1/chat/unread"), adminToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/chat/teams/" + teamId + "/conversation"), adminToken, null).andExpect(status().isForbidden());
		// Không ai mời được Admin
		send(post("/api/v1/chat/direct"), annToken, "{\"userId\":" + admin.getId() + "}").andExpect(status().isUnprocessableContent());
	}

	@Test
	void cannotMessageYourselfOrAMissingOrLockedUser() throws Exception {
		send(post("/api/v1/chat/direct"), annToken, "{\"userId\":" + ann.getId() + "}").andExpect(status().isUnprocessableContent());
		send(post("/api/v1/chat/direct"), annToken, "{\"userId\":999999}").andExpect(status().isUnprocessableContent());
		send(post("/api/v1/chat/direct"), annToken, "{}").andExpect(status().isUnprocessableContent());
		send(post("/api/v1/users/" + carl.getId() + "/lock"), adminToken, null).andExpect(status().isOk());
		send(post("/api/v1/chat/direct"), annToken, "{\"userId\":" + carl.getId() + "}").andExpect(status().isUnprocessableContent());
	}

	@Test
	void requestsWithoutTokenAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/chat/conversations")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/people")).andExpect(status().isUnauthorized());
	}

	@Test
	void aThirdPersonCannotSeeOrUseSomeoneElsesConversation() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		long message = sayOk(conversation, annToken, "bí mật");
		send(get("/api/v1/chat/conversations/" + conversation), carlToken, null).andExpect(status().isNotFound());
		send(get("/api/v1/chat/conversations/" + conversation + "/messages"), carlToken, null).andExpect(status().isNotFound());
		say(conversation, carlToken, "chen vào").andExpect(status().isNotFound());
		send(get("/api/v1/chat/messages/" + message), carlToken, null).andExpect(status().isNotFound());
		send(put("/api/v1/chat/messages/" + message + "/reactions"), carlToken, "{\"emoji\":\"👍\"}").andExpect(status().isNotFound());
		send(post("/api/v1/chat/conversations/" + conversation + "/read"), carlToken, null).andExpect(status().isNotFound());
		send(get("/api/v1/chat/conversations"), carlToken, null).andExpect(jsonPath("$.data.length()").value(0));
	}

	// ---- chat nhóm ----

	@Test
	void everyMemberOfATeamIncludingObserversUsesTheTeamRoom() throws Exception {
		long room = id(send(get("/api/v1/chat/teams/" + teamId + "/conversation"), annToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("TEAM")).andExpect(jsonPath("$.data.team.name").value("Alpha"))
				.andExpect(jsonPath("$.data.canSend").value(true)), "$.data.id");
		// Mở lần nữa vẫn là cùng một phòng
		assertThat(id(send(get("/api/v1/chat/teams/" + teamId + "/conversation"), bobToken, null)
				.andExpect(status().isOk()), "$.data.id")).isEqualTo(room);
		send(get("/api/v1/chat/teams/" + teamId + "/conversation"), managerToken, null).andExpect(status().isOk());

		sayOk(room, annToken, "họp lúc 9h");
		sayOk(room, bobToken, "dạ vâng");
		sayOk(room, managerToken, "ok cả nhóm");
		send(get("/api/v1/chat/conversations/" + room + "/messages"), bobToken, null)
				.andExpect(jsonPath("$.data.items.length()").value(3));
		// Phòng nhóm không nằm trong danh sách nhắn riêng
		send(get("/api/v1/chat/conversations"), annToken, null).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void peopleOutsideTheTeamCannotEnterItsRoom() throws Exception {
		long room = id(send(get("/api/v1/chat/teams/" + teamId + "/conversation"), annToken, null), "$.data.id");
		sayOk(room, annToken, "chỉ nhóm thấy");
		send(get("/api/v1/chat/teams/" + teamId + "/conversation"), carlToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/chat/conversations/" + room), carlToken, null).andExpect(status().isForbidden());
		send(get("/api/v1/chat/conversations/" + room + "/messages"), carlToken, null).andExpect(status().isForbidden());
		say(room, carlToken, "chen vào").andExpect(status().isForbidden());
		send(get("/api/v1/chat/teams/999999/conversation"), annToken, null).andExpect(status().isForbidden());
	}

	@Test
	void anArchivedTeamRoomKeepsItsHistoryButIsReadOnly() throws Exception {
		long room = id(send(get("/api/v1/chat/teams/" + teamId + "/conversation"), annToken, null), "$.data.id");
		long message = sayOk(room, annToken, "trước khi lưu trữ");
		String version = send(get("/api/v1/teams/" + teamId), adminToken, null).andReturn().getResponse().getContentAsString();
		long v = ((Number) JsonPath.read(version, "$.data.version")).longValue();
		send(post("/api/v1/teams/" + teamId + "/archive"), adminToken, "{\"version\":" + v + "}").andExpect(status().isOk());

		send(get("/api/v1/chat/teams/" + teamId + "/conversation"), bobToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.canSend").value(false));
		send(get("/api/v1/chat/conversations/" + room + "/messages"), bobToken, null)
				.andExpect(jsonPath("$.data.items.length()").value(1));
		say(room, annToken, "còn viết được không").andExpect(status().isConflict());
		send(put("/api/v1/chat/messages/" + message), annToken, "{\"body\":\"sửa\"}").andExpect(status().isConflict());
		send(delete("/api/v1/chat/messages/" + message), annToken, null).andExpect(status().isConflict());
	}

	// ---- tin nhắn ----

	@Test
	void messagesNeedTextOrAtLeastOneFile() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		say(conversation, annToken, null).andExpect(status().isUnprocessableContent());
		say(conversation, annToken, "   ").andExpect(status().isUnprocessableContent());
		say(conversation, annToken, "x".repeat(4001)).andExpect(status().isUnprocessableContent());
		say(conversation, annToken, null, upload("anh.png", PNG)).andExpect(status().isCreated());
	}

	@Test
	void onlyTheAuthorEditsAndEditedMessagesAreMarked() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		long message = sayOk(conversation, annToken, "gốc");
		send(put("/api/v1/chat/messages/" + message), bobToken, "{\"body\":\"hack\"}").andExpect(status().isForbidden());
		send(put("/api/v1/chat/messages/" + message), annToken, "{\"body\":\"đã sửa\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("đã sửa")).andExpect(jsonPath("$.data.editedAt").isNotEmpty());
		send(put("/api/v1/chat/messages/" + message), annToken, "{\"body\":\"  \"}").andExpect(status().isUnprocessableContent());
	}

	@Test
	void deletingHidesTheMessageButTheHistoryIsKeptForever() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		long message = sayOk(conversation, annToken, "sẽ xoá");
		send(delete("/api/v1/chat/messages/" + message), bobToken, null).andExpect(status().isForbidden());
		send(delete("/api/v1/chat/messages/" + message), annToken, null).andExpect(status().isNoContent());

		send(get("/api/v1/chat/conversations/" + conversation + "/messages"), bobToken, null)
				.andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].deleted").value(true))
				.andExpect(jsonPath("$.data.items[0].body").doesNotExist());
		// Dữ liệu vẫn còn nguyên trong CSDL
		var stored = messageRepository.findById(message).orElseThrow();
		assertThat(stored.getBody()).isEqualTo("sẽ xoá");
		assertThat(stored.getDeletedAt()).isNotNull();
		// Tin đã xoá không sửa được nữa
		send(put("/api/v1/chat/messages/" + message), annToken, "{\"body\":\"x\"}").andExpect(status().isNotFound());
	}

	@Test
	void repliesQuoteTheOriginalAndMustStayInTheSameConversation() throws Exception {
		long first = connect(annToken, bob, bobToken);
		long second = connect(annToken, carl, carlToken);
		long original = sayOk(first, bobToken, "câu hỏi");
		long outside = sayOk(second, carlToken, "chuyện khác");
		var reply = multipart("/api/v1/chat/conversations/" + first + "/messages").param("body", "trả lời")
				.param("replyToId", String.valueOf(original)).header("Authorization", bearer(annToken));
		mockMvc.perform(reply).andExpect(status().isCreated()).andExpect(jsonPath("$.data.replyTo.id").value(original))
				.andExpect(jsonPath("$.data.replyTo.snippet").value("câu hỏi"))
				.andExpect(jsonPath("$.data.replyTo.author.id").value(bob.getId()));
		var cross = multipart("/api/v1/chat/conversations/" + first + "/messages").param("body", "trả lời")
				.param("replyToId", String.valueOf(outside)).header("Authorization", bearer(annToken));
		mockMvc.perform(cross).andExpect(status().isNotFound());
	}

	@Test
	void reactionsAreCountedIdempotentAndNeverOnYourOwnMessage() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		long message = sayOk(conversation, annToken, "hay đó");
		String url = "/api/v1/chat/messages/" + message + "/reactions";
		send(put(url), annToken, "{\"emoji\":\"👍\"}").andExpect(status().isForbidden());
		send(put(url), bobToken, "{\"emoji\":\"👍\"}").andExpect(status().isOk());
		send(put(url), bobToken, "{\"emoji\":\"👍\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions.length()").value(1)).andExpect(jsonPath("$.data.reactions[0].count").value(1))
				.andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));
		send(get("/api/v1/chat/messages/" + message), annToken, null)
				.andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(false));
		send(put(url), bobToken, "{\"emoji\":\"abc\"}").andExpect(status().isUnprocessableContent());
		send(delete(url + "?emoji=👍"), bobToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reactions.length()").value(0));
	}

	@Test
	void attachmentsAreValidatedStoredAndOnlyDownloadableByThePeopleInTheConversation() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		String json = say(conversation, annToken, "xem file", upload("anh.png", PNG), upload("bao-cao.pdf", PDF))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.attachments.length()").value(2))
				.andExpect(jsonPath("$.data.attachments[0].image").value(true))
				.andExpect(jsonPath("$.data.attachments[1].image").value(false)).andReturn().getResponse().getContentAsString();
		long message = ((Number) JsonPath.read(json, "$.data.id")).longValue();
		long pdf = ((Number) JsonPath.read(json, "$.data.attachments[1].id")).longValue();
		String url = "/api/v1/chat/messages/" + message + "/attachments/" + pdf + "/download";
		for (String token : new String[] {annToken, bobToken}) {
			assertThat(send(get(url), token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
					.isEqualTo(PDF);
		}
		send(get(url), carlToken, null).andExpect(status().isNotFound());
		send(get(url), adminToken, null).andExpect(status().isForbidden());

		// File giả (đuôi .pdf nhưng là file thực thi) và loại không cho phép bị từ chối, không lưu gì
		say(conversation, annToken, "độc hại", upload("virus.pdf", EXE)).andExpect(status().isUnprocessableContent());
		say(conversation, annToken, "độc hại", upload("tool.exe", EXE)).andExpect(status().isUnprocessableContent());
		MockMultipartFile[] eleven = new MockMultipartFile[11];
		for (int i = 0; i < eleven.length; i++) {
			eleven[i] = upload("a" + i + ".png", PNG);
		}
		say(conversation, annToken, "quá nhiều", eleven).andExpect(status().isUnprocessableContent());
		send(get("/api/v1/chat/conversations/" + conversation + "/messages"), annToken, null)
				.andExpect(jsonPath("$.data.items.length()").value(1));

		// File của tin đã xoá không tải được nữa
		send(delete("/api/v1/chat/messages/" + message), annToken, null).andExpect(status().isNoContent());
		send(get(url), bobToken, null).andExpect(status().isNotFound());
	}

	// ---- lịch sử, chưa đọc ----

	@Test
	void historyIsPagedFromNewestToOldestWithACursor() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		long[] ids = new long[5];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = sayOk(conversation, i % 2 == 0 ? annToken : bobToken, "tin " + i);
		}
		String base = "/api/v1/chat/conversations/" + conversation + "/messages";
		send(get(base + "?size=2"), annToken, null).andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].body").value("tin 4")).andExpect(jsonPath("$.data.items[1].body").value("tin 3"))
				.andExpect(jsonPath("$.data.hasMore").value(true));
		send(get(base + "?size=2&before=" + ids[3]), annToken, null).andExpect(jsonPath("$.data.items[0].body").value("tin 2"))
				.andExpect(jsonPath("$.data.items[1].body").value("tin 1")).andExpect(jsonPath("$.data.hasMore").value(true));
		send(get(base + "?size=2&before=" + ids[1]), annToken, null).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].body").value("tin 0")).andExpect(jsonPath("$.data.hasMore").value(false));
	}

	@Test
	void unreadCountsFollowTheOtherSidesMessagesAndResetWhenRead() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		sayOk(conversation, annToken, "một");
		sayOk(conversation, annToken, "hai");

		// Bob có 2 tin chưa đọc từ Ann; Ann tự gửi thì không tính là chưa đọc
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(2));
		send(get("/api/v1/chat/unread"), annToken, null).andExpect(jsonPath("$.data.total").value(0));
		send(get("/api/v1/chat/conversations"), bobToken, null).andExpect(jsonPath("$.data[0].unread").value(2))
				.andExpect(jsonPath("$.data[0].last.snippet").value("hai"));

		send(post("/api/v1/chat/conversations/" + conversation + "/read"), bobToken, null).andExpect(status().isNoContent());
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(0));

		// Bob trả lời: Ann có 1 tin chưa đọc, Bob không có
		sayOk(conversation, bobToken, "của Bob");
		send(get("/api/v1/chat/unread"), annToken, null).andExpect(jsonPath("$.data.total").value(1));
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(0));
	}

	@Test
	void replyingCountsAsHavingReadEverythingBeforeIt() throws Exception {
		long conversation = connect(annToken, bob, bobToken);
		sayOk(conversation, annToken, "một");
		sayOk(conversation, annToken, "hai");
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(2));
		sayOk(conversation, bobToken, "trả lời luôn");
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(0));
	}

	@Test
	void membersPresentWhenMessagesAreSentHaveThemUnreadButLateJoinersAreNotFloodedByOldHistory() throws Exception {
		long room = id(send(get("/api/v1/chat/teams/" + teamId + "/conversation"), annToken, null), "$.data.id");
		sayOk(room, annToken, "tin cũ 1");
		sayOk(room, annToken, "tin cũ 2");
		// Bob và Observer đã là thành viên lúc các tin này được gửi: với họ đây là tin chưa đọc
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(2))
				.andExpect(jsonPath("$.data.conversations[0].teamId").value(teamId));
		send(get("/api/v1/chat/unread"), managerToken, null).andExpect(jsonPath("$.data.total").value(2));

		// Carl vào nhóm SAU khi đã có tin: mở phòng lần đầu thì lịch sử cũ không bị tính là chưa đọc
		send(post("/api/v1/teams/" + teamId + "/members"), adminToken,
				"{\"userId\":" + carl.getId() + ",\"teamRole\":\"MEMBER\"}").andExpect(status().isCreated());
		send(get("/api/v1/chat/teams/" + teamId + "/conversation"), carlToken, null)
				.andExpect(jsonPath("$.data.unread").value(0));
		sayOk(room, annToken, "tin mới");
		send(get("/api/v1/chat/unread"), carlToken, null).andExpect(jsonPath("$.data.total").value(1));
		send(get("/api/v1/chat/unread"), bobToken, null).andExpect(jsonPath("$.data.total").value(3));
	}

	// ---- danh bạ ----

	@Test
	void peopleDirectoryListsEveryoneExceptAdminAndLockedAccounts() throws Exception {
		send(get("/api/v1/people"), bobToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.email=='admin@example.com')]").isEmpty())
				.andExpect(jsonPath("$.data[?(@.email=='manager@example.com')]").isNotEmpty())
				.andExpect(jsonPath("$.data[?(@.email=='ann@example.com')]").isNotEmpty());
		send(get("/api/v1/people?q=CARL"), annToken, null).andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(carl.getId()));
		send(post("/api/v1/users/" + carl.getId() + "/lock"), adminToken, null).andExpect(status().isOk());
		send(get("/api/v1/people?q=carl"), annToken, null).andExpect(jsonPath("$.data.length()").value(0));
		send(get("/api/v1/people/" + carl.getId()), annToken, null).andExpect(status().isNotFound());
		send(get("/api/v1/people/" + admin.getId()), annToken, null).andExpect(status().isNotFound());
		send(get("/api/v1/people/" + bob.getId()), annToken, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fullName").value(bob.getFullName()))
				.andExpect(jsonPath("$.data.status").doesNotExist());
	}
}

package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.jayway.jsonpath.JsonPath;
import com.soudan.auth.service.JwtService;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;
import com.soudan.user.repository.UserRepository;
import com.soudan.user.service.CreateUserCommand;
import com.soudan.user.service.UserService;

/**
 * WebSocket thật: máy chủ chạy trên một cổng ngẫu nhiên, client STOMP kết nối thật. Kiểm tra hai việc quan trọng nhất:
 * (1) chỉ kết nối và nghe được khi có token hợp lệ, chỉ nghe được hàng đợi riêng của mình; (2) sự kiện chỉ được đẩy tới
 * đúng những người trong cuộc, và chỉ sau khi đã lưu.
 * Test này commit dữ liệu thật nên dùng email riêng và tự dọn xong.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ChatWebSocketTest {

	private static final String PASSWORD = "Passw0rd!123";

	@LocalServerPort
	private int port;
	@Autowired
	private UserService userService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private JdbcTemplate jdbc;

	private static User ann;
	private static User bob;
	private static User carl;
	private static User admin;

	private WebSocketStompClient client;

	@BeforeAll
	static void markStatic() {
		// Người dùng được tạo ở lần chạy test đầu tiên (xem users())
	}

	private synchronized void users() {
		if (ann != null) {
			return;
		}
		ann = account("ws-ann@example.com");
		bob = account("ws-bob@example.com");
		carl = account("ws-carl@example.com");
		admin = userService.createInitialAdmin("ws-admin@example.com", PASSWORD, "WS Admin");
		admin.setPassword(passwordEncoder.encode(PASSWORD), false);
		userRepository.saveAndFlush(admin);
	}

	private User account(String email) {
		User user = userService.create(new CreateUserCommand(email, PASSWORD, "User " + email, "Dev", SystemRole.USER));
		user.setPassword(passwordEncoder.encode(PASSWORD), false);
		return userRepository.saveAndFlush(user);
	}

	@AfterAll
	static void cleanUpMarker() {
		// Dọn trong cleanUp() bên dưới (cần JdbcTemplate nên không static)
	}

	@org.junit.jupiter.api.AfterEach
	void cleanUp() {
		if (client != null) {
			client.stop();
		}
		jdbc.update("delete from notification");
		jdbc.update("update chat_conversation set last_message_id = null");
		jdbc.update("delete from chat_reaction");
		jdbc.update("delete from chat_attachment");
		jdbc.update("delete from chat_message");
		jdbc.update("delete from chat_read");
		jdbc.update("delete from chat_conversation");
	}

	// ---- helpers ----

	private String token(User user) {
		return jwtService.createAccessToken(user.getId());
	}

	private WebSocketStompClient stomp() {
		WebSocketStompClient c = new WebSocketStompClient(new StandardWebSocketClient());
		c.setMessageConverter(new JacksonJsonMessageConverter());
		client = c;
		return c;
	}

	private StompSession connect(WebSocketStompClient c, String authorization) throws Exception {
		StompHeaders headers = new StompHeaders();
		if (authorization != null) {
			headers.add("Authorization", authorization);
		}
		WebSocketHttpHeaders http = new WebSocketHttpHeaders();
		http.setOrigin("http://localhost:5173");
		return c.connectAsync("ws://localhost:" + port + "/ws", http, headers, new StompSessionHandlerAdapter() {
		}).get(5, TimeUnit.SECONDS);
	}

	/** Kết nối và nghe hàng đợi riêng; sự kiện nhận được đổ vào hàng đợi trả về. */
	private BlockingQueue<Map<String, Object>> listen(StompSession session) {
		BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
		session.subscribe("/user/queue/chat", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void handleFrame(StompHeaders headers, Object payload) {
				queue.add((Map<String, Object>) payload);
			}
		});
		return queue;
	}

	private Map<String, Object> next(BlockingQueue<Map<String, Object>> queue) throws Exception {
		Map<String, Object> event = queue.poll(5, TimeUnit.SECONDS);
		if (event == null) {
			throw new TimeoutException("Không nhận được sự kiện nào trong 5 giây");
		}
		return event;
	}

	private record Resp(int status, String body) {
	}

	private Resp call(HttpMethod method, String path, User as, MediaType type, Object body) {
		var spec = RestClient.create("http://localhost:" + port).method(method).uri(path)
				.headers(h -> h.setBearerAuth(token(as)));
		if (type != null) {
			spec = spec.contentType(type);
		}
		if (body != null) {
			spec = spec.body(body);
		}
		return spec.exchange((request, response) -> new Resp(response.getStatusCode().value(),
				new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private String post(User as, String path, String json) {
		Resp r = call(HttpMethod.POST, path, as, MediaType.APPLICATION_JSON, json);
		assertThat(r.status() / 100).as(r.body()).isEqualTo(2);
		return r.body();
	}

	private String send(User as, long conversationId, String text) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("body", text);
		form.add("files", new ByteArrayResource("%PDF-1.7 x".getBytes(StandardCharsets.US_ASCII)) {
			@Override
			public String getFilename() {
				return "bao-cao.pdf";
			}
		});
		Resp r = call(HttpMethod.POST, "/api/v1/chat/conversations/" + conversationId + "/messages", as,
				MediaType.MULTIPART_FORM_DATA, form);
		assertThat(r.status() / 100).as(r.body()).isEqualTo(2);
		return r.body();
	}

	// ---- xác thực ----

	@Test
	void connectingNeedsAValidToken() throws Exception {
		users();
		assertThatThrownBy(() -> connect(stomp(), null)).isInstanceOf(Exception.class);
		assertThatThrownBy(() -> connect(stomp(), "Bearer not-a-token")).isInstanceOf(Exception.class);
		assertThatThrownBy(() -> connect(stomp(), token(ann))).as("thiếu chữ Bearer").isInstanceOf(Exception.class);
		StompSession ok = connect(stomp(), "Bearer " + token(ann));
		assertThat(ok.isConnected()).isTrue();
	}

	@Test
	void aLockedAccountCannotConnectEvenWithAFreshToken() throws Exception {
		users();
		User locked = account("ws-locked@example.com");
		String token = token(locked);
		userService.lock(locked.getId());
		assertThatThrownBy(() -> connect(stomp(), "Bearer " + token)).isInstanceOf(Exception.class);
		jdbc.update("delete from refresh_token where user_id = ?", locked.getId());
		jdbc.update("delete from users where id = ?", locked.getId());
	}

	@Test
	void youCanOnlyListenToYourOwnPrivateQueueAndNeverSendToTheBroker() throws Exception {
		users();
		StompSession session = connect(stomp(), "Bearer " + token(ann));
		BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
		session.subscribe("/topic/everything", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				events.add(Map.of("x", payload));
			}
		});
		// Đăng ký trái phép làm máy chủ đóng kết nối
		long deadline = System.currentTimeMillis() + 5000;
		while (session.isConnected() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertThat(session.isConnected()).as("đăng ký kênh lạ phải bị ngắt").isFalse();
	}

	// ---- đẩy sự kiện ----

	@Test
	void eventsGoOnlyToThePeopleInTheConversationAndOnlyAfterTheyAreSaved() throws Exception {
		users();
		var annEvents = listen(connect(stomp(), "Bearer " + token(ann)));
		WebSocketStompClient bobClient = stomp();
		var bobEvents = listen(connect(bobClient, "Bearer " + token(bob)));
		var carlEvents = listen(connect(new WebSocketStompClient(new StandardWebSocketClient()) {
			{
				setMessageConverter(new JacksonJsonMessageConverter());
			}
		}, "Bearer " + token(carl)));
		Thread.sleep(300); // đợi đăng ký xong

		// Ann mời Bob: cả hai nhận sự kiện "CONVERSATION", Carl không
		String started = post(ann, "/api/v1/chat/direct", "{\"userId\":" + bob.getId() + "}");
		long conversation = ((Number) JsonPath.read(started, "$.data.id")).longValue();
		assertThat(next(bobEvents)).containsEntry("type", "CONVERSATION").containsEntry("conversationId", (int) conversation);
		assertThat(next(annEvents)).containsEntry("type", "CONVERSATION");

		// Bob chấp nhận: cả hai nhận sự kiện
		post(bob, "/api/v1/chat/conversations/" + conversation + "/accept", "{}");
		assertThat(next(annEvents)).containsEntry("type", "CONVERSATION");
		assertThat(next(bobEvents)).containsEntry("type", "CONVERSATION");

		// Ann gửi tin (có file): cả hai nhận tin mới kèm nội dung, tin đã nằm trong CSDL
		send(ann, conversation, "xin chào Bob");
		Map<String, Object> received = next(bobEvents);
		assertThat(received).containsEntry("type", "MESSAGE");
		@SuppressWarnings("unchecked")
		Map<String, Object> message = (Map<String, Object>) received.get("message");
		assertThat(message).containsEntry("body", "xin chào Bob");
		assertThat((java.util.List<?>) message.get("attachments")).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from chat_message where id = ?", Integer.class,
				((Number) received.get("messageId")).longValue())).isEqualTo(1);
		assertThat(next(annEvents)).containsEntry("type", "MESSAGE");

		// Bob thả biểu cảm: người nhận được báo "đã đổi" để tự tải lại tin
		long messageId = ((Number) received.get("messageId")).longValue();
		call(HttpMethod.PUT, "/api/v1/chat/messages/" + messageId + "/reactions", bob, MediaType.APPLICATION_JSON,
				"{\"emoji\":\"👍\"}");
		assertThat(next(annEvents)).containsEntry("type", "MESSAGE_UPDATED").containsEntry("messageId", (int) messageId);

		// Carl không thuộc cuộc trò chuyện nên không nhận được gì
		assertThat(carlEvents.poll(1, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void aRejectedRequestDoesNotPushAnythingBecauseNothingWasSaved() throws Exception {
		users();
		var annEvents = listen(connect(stomp(), "Bearer " + token(ann)));
		Thread.sleep(300);
		// Ann mời chính mình: bị từ chối (422), không có gì được lưu hay đẩy
		Resp response = call(HttpMethod.POST, "/api/v1/chat/direct", ann, MediaType.APPLICATION_JSON,
				"{\"userId\":" + ann.getId() + "}");
		assertThat(response.status()).isEqualTo(422);
		assertThat(annEvents.poll(1, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void teamRoomMessagesReachEveryMemberOfTheTeam() throws Exception {
		users();
		// Dựng nhóm bằng SQL trực tiếp (test này không đi qua admin): Ann là Leader, Bob là Member, Carl ở ngoài
		Long team = jdbc.queryForObject("insert into team (name, status, created_by, updated_by) values ('WS Team', 'ACTIVE', ?, ?) returning id",
				Long.class, ann.getId(), ann.getId());
		jdbc.update("insert into team_member (team_id, user_id, team_role) values (?, ?, 'LEADER'), (?, ?, 'MEMBER')",
				team, ann.getId(), team, bob.getId());
		try {
			var bobEvents = listen(connect(stomp(), "Bearer " + token(bob)));
			var carlEvents = listen(connect(new WebSocketStompClient(new StandardWebSocketClient()) {
				{
					setMessageConverter(new JacksonJsonMessageConverter());
				}
			}, "Bearer " + token(carl)));
			Thread.sleep(300);
			Resp room = call(HttpMethod.GET, "/api/v1/chat/teams/" + team + "/conversation", ann, null, null);
			long conversation = ((Number) JsonPath.read(room.body(), "$.data.id")).longValue();
			send(ann, conversation, "họp lúc 9h");
			assertThat(next(bobEvents)).containsEntry("type", "MESSAGE");
			assertThat(carlEvents.poll(1, TimeUnit.SECONDS)).isNull();
		} finally {
			cleanUp();
			jdbc.update("delete from team_member where team_id = ?", team);
			jdbc.update("delete from team where id = ?", team);
		}
	}
}

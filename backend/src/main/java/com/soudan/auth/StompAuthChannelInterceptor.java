package com.soudan.auth;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.soudan.auth.service.JwtService;
import com.soudan.common.exception.UnauthorizedException;
import com.soudan.user.UserAccount;
import com.soudan.user.UserApi;

/**
 * Xác thực kết nối WebSocket (STOMP). Trình duyệt không gửi được header Authorization lúc bắt tay WebSocket, nên token
 * truy cập được gửi trong khung CONNECT; ở đây kiểm tra token, đọc lại tài khoản từ CSDL (khoá hay đổi mật khẩu có hiệu
 * lực ngay) rồi đặt người dùng vào phiên. Quy tắc sau đó:
 * <ul>
 * <li>Không có token hợp lệ thì không kết nối được.</li>
 * <li>Chỉ được đăng ký (SUBSCRIBE) hàng đợi RIÊNG của mình ({@code /user/queue/...}); không nghe được của người khác.</li>
 * <li>Client không được tự gửi (SEND) vào broker: mọi thao tác ghi đi qua REST có kiểm tra quyền, server mới là bên đẩy sự kiện.</li>
 * </ul>
 * Đặt ở package gốc của module auth để config dùng được.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final String BEARER = "Bearer ";
	private static final String USER_QUEUE_PREFIX = "/user/queue/";

	/** Người dùng của một phiên WebSocket; {@code getName()} là id tài khoản để định tuyến {@code /user/...}. */
	public record SessionUser(Long id) implements Principal {
		@Override
		public String getName() {
			return String.valueOf(id);
		}
	}

	private final JwtService jwtService;
	private final UserApi userApi;

	public StompAuthChannelInterceptor(JwtService jwtService, UserApi userApi) {
		this.jwtService = jwtService;
		this.userApi = userApi;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		StompCommand command = accessor.getCommand();
		if (StompCommand.CONNECT.equals(command)) {
			accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
		} else if (StompCommand.SUBSCRIBE.equals(command)) {
			String destination = accessor.getDestination();
			if (accessor.getUser() == null || destination == null || !destination.startsWith(USER_QUEUE_PREFIX)) {
				throw new MessagingException("Không được đăng ký kênh này");
			}
		} else if (StompCommand.SEND.equals(command)) {
			throw new MessagingException("Client không được gửi trực tiếp vào broker; hãy dùng REST");
		}
		return message;
	}

	private Principal authenticate(String header) {
		if (header == null || !header.startsWith(BEARER)) {
			throw new MessagingException("Thiếu token");
		}
		try {
			Long userId = jwtService.parseUserId(header.substring(BEARER.length()).trim());
			UserAccount account = userApi.findAccountById(userId).orElse(null);
			if (account == null || !account.active() || account.mustChangePassword()) {
				throw new MessagingException("Tài khoản không dùng được");
			}
			return new SessionUser(account.id());
		} catch (UnauthorizedException e) {
			throw new MessagingException("Token không hợp lệ", e);
		}
	}
}

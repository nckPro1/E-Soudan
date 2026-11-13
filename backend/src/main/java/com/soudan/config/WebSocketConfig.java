package com.soudan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.soudan.auth.StompAuthChannelInterceptor;

/**
 * WebSocket (STOMP) cho trò chuyện thời gian thực. Máy chủ đẩy sự kiện tới hàng đợi riêng của từng người
 * ({@code /user/queue/chat}); client chỉ nghe, mọi thao tác ghi đi qua REST. Kết nối được xác thực bằng token ở khung
 * CONNECT (xem {@link StompAuthChannelInterceptor}). Dùng broker trong bộ nhớ nên chạy một bản máy chủ; khi cần chạy
 * nhiều bản thì đổi sang broker ngoài (RabbitMQ/Redis) mà không đổi code nghiệp vụ.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthChannelInterceptor authInterceptor;
	private final String[] allowedOrigins;

	public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
			@Value("${app.websocket.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
		this.authInterceptor = authInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue");
		registry.setUserDestinationPrefix("/user");
		// Không nhận tin từ client qua STOMP: không có tiền tố ứng dụng nào được mở
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}
}

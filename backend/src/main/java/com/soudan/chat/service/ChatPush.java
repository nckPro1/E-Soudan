package com.soudan.chat.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.soudan.chat.service.ChatViews.MessageView;

/**
 * Đẩy sự kiện thời gian thực (WebSocket/STOMP) tới trình duyệt của từng người qua hàng đợi riêng {@code /user/queue/chat}.
 * Chỉ đẩy SAU KHI giao dịch commit, để người nhận không bao giờ nhận tin chưa được lưu. Sự kiện chỉ mang tin mới
 * (dữ liệu chung cho mọi người) hoặc id để client tự tải lại, không mang dữ liệu riêng của từng người xem.
 * Người nhận do server quyết định từ thành viên cuộc trò chuyện, client không tự đăng ký vào cuộc trò chuyện nào.
 */
@Component
class ChatPush {

	/** Loại sự kiện gửi cho client. */
	enum Type {
		/** Có tin mới (kèm nội dung tin). */
		MESSAGE,
		/** Tin cũ bị sửa, xoá hoặc đổi biểu cảm: client tải lại tin theo id. */
		MESSAGE_UPDATED,
		/** Lời mời hoặc trạng thái cuộc trò chuyện đổi: client tải lại cuộc trò chuyện theo id. */
		CONVERSATION,
		/** Cuộc trò chuyện không còn (lời mời bị thu hồi): client bỏ nó khỏi danh sách. */
		CONVERSATION_REMOVED
	}

	/** Gói tin đẩy xuống client. */
	record Event(Type type, Long conversationId, Long messageId, MessageView message) {
	}

	private static final Logger log = LoggerFactory.getLogger(ChatPush.class);
	private static final String DESTINATION = "/queue/chat";

	private final SimpMessagingTemplate template;

	ChatPush(SimpMessagingTemplate template) {
		this.template = template;
	}

	void message(Collection<Long> recipients, MessageView message) {
		send(recipients, new Event(Type.MESSAGE, message.conversationId(), message.id(), message));
	}

	void messageUpdated(Collection<Long> recipients, Long conversationId, Long messageId) {
		send(recipients, new Event(Type.MESSAGE_UPDATED, conversationId, messageId, null));
	}

	void conversation(Collection<Long> recipients, Long conversationId) {
		send(recipients, new Event(Type.CONVERSATION, conversationId, null, null));
	}

	void conversationRemoved(Collection<Long> recipients, Long conversationId) {
		send(recipients, new Event(Type.CONVERSATION_REMOVED, conversationId, null, null));
	}

	private void send(Collection<Long> recipients, Event event) {
		Set<Long> unique = new LinkedHashSet<>(recipients);
		if (unique.isEmpty()) {
			return;
		}
		Runnable deliver = () -> {
			for (Long userId : unique) {
				try {
					template.convertAndSendToUser(String.valueOf(userId), DESTINATION, event);
				} catch (RuntimeException e) {
					// Đẩy thất bại không được làm hỏng nghiệp vụ; client sẽ thấy dữ liệu khi tải lại
					log.warn("Không đẩy được sự kiện chat tới người dùng {}", userId, e);
				}
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deliver.run();
				}
			});
		} else {
			deliver.run();
		}
	}
}

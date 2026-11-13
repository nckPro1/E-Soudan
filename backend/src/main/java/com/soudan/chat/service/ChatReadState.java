package com.soudan.chat.service;

import org.springframework.stereotype.Component;

import com.soudan.chat.domain.ChatRead;
import com.soudan.chat.repository.ChatReadRepository;

/**
 * Mốc "đã đọc tới đâu" của từng người trong từng cuộc trò chuyện. Số chưa đọc chỉ tính cho người đã có mốc, nên mốc
 * phải được tạo kịp thời: khi nhận lời mời (nhắn riêng) hoặc khi có tin mới gửi vào phòng nhóm (mốc đặt ngay trước tin
 * đó), để người mới vào nhóm không bị "ngập" bởi cả lịch sử cũ.
 */
@Component
class ChatReadState {

	private final ChatReadRepository reads;

	ChatReadState(ChatReadRepository reads) {
		this.reads = reads;
	}

	/** Tạo mốc nếu chưa có, bắt đầu từ {@code startAt}; đã có thì giữ nguyên. */
	void ensure(Long conversationId, Long userId, long startAt) {
		ChatRead.Key key = new ChatRead.Key(conversationId, userId);
		if (!reads.existsById(key)) {
			reads.save(new ChatRead(conversationId, userId, startAt));
		}
	}

	/** Đẩy mốc tới {@code messageId} (tạo nếu chưa có). Mốc chỉ tiến, không lùi. */
	void advance(Long conversationId, Long userId, long messageId) {
		ChatRead read = reads.findById(new ChatRead.Key(conversationId, userId))
				.orElseGet(() -> new ChatRead(conversationId, userId, 0));
		read.advanceTo(messageId);
		reads.save(read);
	}
}

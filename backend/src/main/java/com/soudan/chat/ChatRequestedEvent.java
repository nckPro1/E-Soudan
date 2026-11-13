package com.soudan.chat;

/** Phát ra khi có người gửi lời mời nhắn tin. Module notification lắng nghe để báo cho người nhận. */
public record ChatRequestedEvent(Long conversationId, Long requesterId, Long recipientId) {
}

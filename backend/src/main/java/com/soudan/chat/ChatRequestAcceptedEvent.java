package com.soudan.chat;

/** Phát ra khi lời mời nhắn tin được chấp nhận. Người đã gửi lời mời cần được báo là giờ đã nhắn được. */
public record ChatRequestAcceptedEvent(Long conversationId, Long accepterId, Long requesterId) {
}

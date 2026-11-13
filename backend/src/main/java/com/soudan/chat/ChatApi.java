package com.soudan.chat;

import java.time.Instant;

import com.soudan.common.ai.KnowledgeDoc;

/**
 * Giao diện công khai của module chat cho module khác (trợ lý AI). Chỉ dùng được với cuộc trò chuyện mà người đang đăng
 * nhập tham gia; Admin bị chặn như mọi thao tác trò chuyện khác.
 */
public interface ChatApi {

	/**
	 * Bản ghi các tin (chưa xoá) của một cuộc trò chuyện từ mốc {@code since}, cũ trước mới sau, gộp thành một mẩu văn bản.
	 * Dùng khi người dùng chủ động bấm tóm tắt đúng cuộc trò chuyện đó. Không có tin nào thì {@code text} rỗng.
	 */
	KnowledgeDoc transcript(Long conversationId, Instant since, int limit);
}

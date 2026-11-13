package com.soudan.ai.llm;

import java.util.List;

/** Cổng gọi mô hình ngôn ngữ. Test thay bằng bản giả; chạy thật dùng {@link AnthropicLlmClient}. */
public interface LlmClient {

	/** Một lượt hội thoại: role là "user" hoặc "assistant". */
	record Turn(String role, String content) {
	}

	/** Gọi mô hình và trả về phần chữ của câu trả lời. Lỗi thì ném {@code BusinessException} có mã AI_*. */
	String complete(String system, List<Turn> turns, int maxTokens);
}

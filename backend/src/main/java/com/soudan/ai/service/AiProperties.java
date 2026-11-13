package com.soudan.ai.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình trợ lý AI ({@code app.ai.*}).
 *
 * @param provider        anthropic (gọi Claude), local (mô hình chạy tại chỗ qua giao thức tương thích OpenAI: Ollama, vLLM...)
 * hoặc mock (bản giả lập để chạy thử, không gọi ra ngoài)
 * @param apiKey          khoá API; để trống thì tính năng AI báo "chưa cấu hình"
 * @param model           mã mô hình
 * @param baseUrl         địa chỉ API
 * @param maxTokens       số token tối đa cho mỗi câu trả lời
 * @param requestsPerHour số lượt tối đa mỗi người mỗi giờ
 * @param timeout         thời gian chờ mô hình
 * @param retrievalDays   truy xuất dữ liệu trong bao nhiêu ngày gần đây
 * @param topDocs         số mẩu dữ liệu đưa vào ngữ cảnh cho hỏi đáp
 * @param maxContextChars tổng số ký tự dữ liệu tối đa đưa cho mô hình trong một lượt
 */
@ConfigurationProperties("app.ai")
public record AiProperties(
		@DefaultValue("anthropic") String provider,
		@DefaultValue("") String apiKey,
		@DefaultValue("claude-sonnet-5-5") String model,
		@DefaultValue("https://api.anthropic.com") String baseUrl,
		@DefaultValue("1500") int maxTokens,
		@DefaultValue("40") int requestsPerHour,
		@DefaultValue("60s") Duration timeout,
		@DefaultValue("60") int retrievalDays,
		@DefaultValue("8") int topDocs,
		@DefaultValue("24000") int maxContextChars) {

	public boolean isMock() {
		return "mock".equalsIgnoreCase(provider);
	}

	public boolean isLocal() {
		return "local".equalsIgnoreCase(provider);
	}

	/** Dùng được: có khoá thật, hoặc dùng mô hình tại chỗ (không cần khoá), hoặc đang ở chế độ giả lập. */
	public boolean isConfigured() {
		return isMock() || isLocal() || !apiKey.isBlank();
	}
}

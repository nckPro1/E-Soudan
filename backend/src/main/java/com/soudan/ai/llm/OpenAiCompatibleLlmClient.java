package com.soudan.ai.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.soudan.ai.service.AiProperties;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

/**
 * Gọi mô hình chạy TẠI CHỖ (Ollama, vLLM, LM Studio...) qua giao thức tương thích OpenAI ({@code /v1/chat/completions}).
 * Dữ liệu không rời mạng nội bộ. Chất lượng và tốc độ phụ thuộc mô hình và máy chủ; mô hình nhỏ cần ngữ cảnh ngắn hơn
 * ({@code app.ai.max-context-chars}) và thời gian chờ dài hơn ({@code app.ai.timeout}).
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

	private final RestClient http;
	private final AiProperties props;

	public OpenAiCompatibleLlmClient(AiProperties props) {
		this.props = props;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) Math.min(props.timeout().toMillis(), 10_000));
		factory.setReadTimeout((int) props.timeout().toMillis());
		RestClient.Builder builder = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory);
		if (!props.apiKey().isBlank()) {
			builder.defaultHeader("Authorization", "Bearer " + props.apiKey());
		}
		this.http = builder.build();
	}

	@Override
	@SuppressWarnings("unchecked")
	public String complete(String system, List<Turn> turns, int maxTokens) {
		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", system));
		for (Turn t : turns) {
			messages.add(Map.of("role", t.role(), "content", t.content()));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", props.model());
		body.put("messages", messages);
		body.put("max_tokens", maxTokens);
		body.put("temperature", 0.2);
		body.put("stream", false);
		try {
			Map<String, Object> response = http.post().uri("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
					.body(body).retrieve().body(Map.class);
			if (response != null && response.get("choices") instanceof List<?> choices && !choices.isEmpty()
					&& choices.get(0) instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message
					&& message.get("content") instanceof String text && !text.isBlank()) {
				return text.strip();
			}
			throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
		} catch (RestClientException e) {
			log.warn("Gọi mô hình tại chỗ thất bại: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
		}
	}
}

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

/** Gọi Claude qua Messages API. Khoá lấy từ cấu hình, không bao giờ ghi vào log. */
public class AnthropicLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
	private static final String VERSION = "2023-06-01";

	private final RestClient http;
	private final AiProperties props;

	public AnthropicLlmClient(AiProperties props) {
		this.props = props;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) Math.min(props.timeout().toMillis(), 10_000));
		factory.setReadTimeout((int) props.timeout().toMillis());
		this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory)
				.defaultHeader("x-api-key", props.apiKey()).defaultHeader("anthropic-version", VERSION).build();
	}

	@Override
	@SuppressWarnings("unchecked")
	public String complete(String system, List<Turn> turns, int maxTokens) {
		List<Map<String, String>> messages = new ArrayList<>();
		for (Turn t : turns) {
			messages.add(Map.of("role", t.role(), "content", t.content()));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", props.model());
		body.put("max_tokens", maxTokens);
		body.put("system", system);
		body.put("messages", messages);
		try {
			Map<String, Object> response = http.post().uri("/v1/messages").contentType(MediaType.APPLICATION_JSON)
					.body(body).retrieve().body(Map.class);
			StringBuilder text = new StringBuilder();
			if (response != null && response.get("content") instanceof List<?> blocks) {
				for (Object block : blocks) {
					if (block instanceof Map<?, ?> b && "text".equals(b.get("type")) && b.get("text") != null) {
						text.append(b.get("text"));
					}
				}
			}
			if (text.isEmpty()) {
				throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
			}
			return text.toString().strip();
		} catch (RestClientException e) {
			// Chỉ ghi loại lỗi, không ghi nội dung yêu cầu (có dữ liệu công ty) hay khoá
			log.warn("Gọi mô hình thất bại: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
		}
	}
}

package com.soudan.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.soudan.ai.llm.LlmClient.Turn;
import com.soudan.ai.service.AiProperties;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;

/** Máy chủ giả đóng vai Ollama/vLLM: kiểm tra yêu cầu gửi đi đúng dạng và đọc được câu trả lời. */
class OpenAiCompatibleLlmClientTest {

	private HttpServer server;

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private AiProperties start(int status, String responseBody, AtomicReference<String> seenBody) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		return new AiProperties("local", "", "qwen2.5:7b", "http://127.0.0.1:" + server.getAddress().getPort(), 500, 40,
				Duration.ofSeconds(5), 60, 8, 24000);
	}

	@Test
	void should_sendSystemAndTurnsAndReturnContent() throws Exception {
		AtomicReference<String> seen = new AtomicReference<>();
		AiProperties props = start(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\" Xin chào [1] \"}}]}", seen);

		String text = new OpenAiCompatibleLlmClient(props).complete("hệ thống", List.of(new Turn("user", "câu hỏi")), 300);

		assertThat(text).isEqualTo("Xin chào [1]");
		assertThat(seen.get()).contains("\"model\":\"qwen2.5:7b\"").contains("\"role\":\"system\"")
				.contains("hệ thống").contains("câu hỏi").contains("\"max_tokens\":300").contains("\"stream\":false");
	}

	@Test
	void should_failWithUpstreamError_when_serverErrorsOrReturnsNoContent() throws Exception {
		AtomicReference<String> seen = new AtomicReference<>();
		AiProperties broken = start(500, "{\"error\":\"boom\"}", seen);
		assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(broken).complete("s", List.of(new Turn("user", "q")), 10))
				.isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
				.isEqualTo(ErrorCode.AI_UPSTREAM_ERROR);
		server.stop(0);

		AiProperties empty = start(200, "{\"choices\":[]}", seen);
		assertThatThrownBy(() -> new OpenAiCompatibleLlmClient(empty).complete("s", List.of(new Turn("user", "q")), 10))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void should_treatLocalProviderAsConfiguredWithoutKey() {
		AiProperties props = new AiProperties("local", "", "m", "http://localhost:11434", 500, 40, Duration.ofSeconds(5), 60, 8,
				24000);
		assertThat(props.isConfigured()).isTrue();
		assertThat(new AiProperties("anthropic", "", "m", "x", 1, 1, Duration.ofSeconds(1), 1, 1, 1).isConfigured()).isFalse();
	}
}

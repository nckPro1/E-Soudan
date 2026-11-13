package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.soudan.common.security.SystemRole;

/** Chưa có khoá API: ứng dụng vẫn chạy, trợ lý báo "chưa cấu hình" thay vì lỗi khó hiểu. */
@TestPropertySource(properties = { "app.ai.provider=anthropic", "app.ai.api-key=" })
class AiNotConfiguredTest extends IntegrationTestBase {

	@Test
	void should_answer503_when_noApiKey() throws Exception {
		createUser("ann@example.com", SystemRole.USER);
		String token = accessTokenOf("ann@example.com");

		mockMvc.perform(get("/api/v1/ai/status").header("Authorization", bearer(token))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(false));
		mockMvc.perform(post("/api/v1/ai/ask").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"xin chào\"}"))
				.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
	}
}

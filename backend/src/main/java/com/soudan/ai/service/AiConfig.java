package com.soudan.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.soudan.ai.llm.AnthropicLlmClient;
import com.soudan.ai.llm.LlmClient;
import com.soudan.ai.llm.MockLlmClient;
import com.soudan.ai.llm.OpenAiCompatibleLlmClient;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

@Configuration
class AiConfig {

	/** Test có thể khai báo bean LlmClient riêng; khi đó bean này không được tạo. */
	@Bean
	@ConditionalOnMissingBean(LlmClient.class)
	LlmClient llmClient(AiProperties props) {
		if (props.isMock()) {
			return new MockLlmClient();
		}
		if (props.isLocal()) {
			return new OpenAiCompatibleLlmClient(props);
		}
		if (props.apiKey().isBlank()) {
			// Chưa có khoá: mọi lời gọi báo "chưa cấu hình" thay vì làm ứng dụng không khởi động được
			return (system, turns, maxTokens) -> {
				throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
			};
		}
		return new AnthropicLlmClient(props);
	}
}

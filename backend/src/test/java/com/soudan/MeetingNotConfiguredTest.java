package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.soudan.common.security.SystemRole;

/** Chưa cấu hình JaaS (mặc định): báo rõ là chưa cấu hình, không lộ gì và không lỗi hệ thống. */
class MeetingNotConfiguredTest extends IntegrationTestBase {

	@Test
	void should_reportDisabled_withoutLeakingAnything() throws Exception {
		mockMvc.perform(get("/api/v1/public/meetings/config")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.appId").doesNotExist());
	}

	@Test
	void should_answer503_whenCreatingMeeting() throws Exception {
		createUser("host2@soudan.vn", SystemRole.USER);
		String token = accessTokenOf("host2@soudan.vn");

		mockMvc.perform(post("/api/v1/meetings").header("Authorization", "Bearer " + token))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("MEETING_NOT_CONFIGURED"));
	}

	@Test
	void should_answer503_whenGuestJoins() throws Exception {
		mockMvc.perform(post("/api/v1/public/meetings/abcdefghijkm2345/join"))
				.andExpect(status().isServiceUnavailable());
	}
}

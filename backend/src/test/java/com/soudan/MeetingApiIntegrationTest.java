package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.soudan.common.security.SystemRole;

/** Họp video khi đã cấu hình JaaS: chủ trì phải đăng nhập, khách vào bằng link không cần tài khoản. */
class MeetingApiIntegrationTest extends IntegrationTestBase {

	@DynamicPropertySource
	static void meeting(DynamicPropertyRegistry registry) throws Exception {
		KeyPair pair = MeetingTokenServiceTest.newKeyPair();
		registry.add("app.meeting.app-id", () -> "vpaas-magic-cookie-test");
		registry.add("app.meeting.key-id", () -> "vpaas-magic-cookie-test/k1");
		registry.add("app.meeting.private-key", () -> MeetingTokenServiceTest.pem(pair));
	}

	@Test
	void should_exposePublicConfig_withoutLogin() throws Exception {
		mockMvc.perform(get("/api/v1/public/meetings/config")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(true))
				.andExpect(jsonPath("$.data.appId").value("vpaas-magic-cookie-test"))
				.andExpect(jsonPath("$.data.domain").value("8x8.vc"))
				.andExpect(jsonPath("$.data.privateKey").doesNotExist());
	}

	@Test
	void should_requireLogin_toCreateMeeting() throws Exception {
		mockMvc.perform(post("/api/v1/meetings")).andExpect(status().isUnauthorized());
	}

	@Test
	void should_createMeeting_forLoggedInUser() throws Exception {
		createUser("host@soudan.vn", SystemRole.USER);
		String token = accessTokenOf("host@soudan.vn");

		mockMvc.perform(post("/api/v1/meetings").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Cô Lan\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.room").isNotEmpty())
				.andExpect(jsonPath("$.data.jwt").isNotEmpty());
	}

	@Test
	void should_letGuestJoinByLink_withoutAccount() throws Exception {
		mockMvc.perform(post("/api/v1/public/meetings/abcdefghijkm2345/join")
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Khách A\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.jwt").isNotEmpty())
				.andExpect(jsonPath("$.data.room").value("abcdefghijkm2345"));
	}

	@Test
	void should_rejectMalformedRoomCode() throws Exception {
		mockMvc.perform(post("/api/v1/public/meetings/BAD..ROOM/join")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_MEETING_ROOM"));
	}
}

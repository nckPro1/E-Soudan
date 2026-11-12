package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.soudan.auth.service.JwtProperties;
import com.soudan.auth.service.JwtService;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;

class AuthIntegrationTest extends IntegrationTestBase {

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	void should_returnTokens_when_credentialsCorrect() throws Exception {
		createUser("a@example.com", SystemRole.USER);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"A@Example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
	}

	@Test
	void should_return401InvalidCredentials_when_passwordWrong() throws Exception {
		createUser("a@example.com", SystemRole.USER);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@example.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void should_return401WithSameCode_when_emailUnknown() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\",\"password\":\"whatever-123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void should_return403AccountLocked_when_lockedAccountUsesCorrectPassword() throws Exception {
		User user = createUser("locked@example.com", SystemRole.USER);
		userService.lock(user.getId());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"locked@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	@Test
	void should_notRevealLock_when_lockedAccountUsesWrongPassword() throws Exception {
		User user = createUser("locked@example.com", SystemRole.USER);
		userService.lock(user.getId());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"locked@example.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void should_return422WithFieldDetails_when_loginBodyBlank() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"\",\"password\":\"\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.length()").value(2));
	}

	@Test
	void should_return401_when_noToken() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void should_return401_when_tokenGarbage() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Authorization", bearer("not-a-jwt")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void should_return401_when_tokenSignedWithOtherSecret() throws Exception {
		User user = createUser("a@example.com", SystemRole.USER);
		JwtProperties other = new JwtProperties("another-secret-0123456789-0123456789-xx",
				Duration.ofMinutes(15), Duration.ofDays(7));
		String forged = new JwtService(other, Clock.systemUTC()).createAccessToken(user.getId());

		mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(forged)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void should_return401TokenExpired_when_accessTokenPastExpiry() throws Exception {
		User user = createUser("a@example.com", SystemRole.USER);
		Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
		String expired = new JwtService(jwtProperties, twoHoursAgo).createAccessToken(user.getId());

		mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(expired)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void should_return200_when_validToken() throws Exception {
		createUser("a@example.com", SystemRole.USER);

		mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(accessTokenOf("a@example.com"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("a@example.com"))
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());
	}

	@Test
	void should_rotateRefreshToken_when_refreshUsed() throws Exception {
		createUser("a@example.com", SystemRole.USER);
		String refresh = JsonPath.read(login("a@example.com", PASSWORD), "$.data.refreshToken");

		String body = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String newRefresh = JsonPath.read(body, "$.data.refreshToken");
		org.junit.jupiter.api.Assertions.assertNotEquals(refresh, newRefresh);
	}

	@Test
	void should_revokeAllTokens_when_oldRefreshTokenReused() throws Exception {
		createUser("a@example.com", SystemRole.USER);
		String refresh = JsonPath.read(login("a@example.com", PASSWORD), "$.data.refreshToken");
		String body = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(refresh))).andReturn().getResponse().getContentAsString();
		String newRefresh = JsonPath.read(body, "$.data.refreshToken");

		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
				.andExpect(status().isUnauthorized());

		// Token mới cũng bị thu hồi vì token cũ bị dùng lại (nghi bị đánh cắp).
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(newRefresh)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void should_return401_when_refreshAfterLogout() throws Exception {
		createUser("a@example.com", SystemRole.USER);
		String refresh = JsonPath.read(login("a@example.com", PASSWORD), "$.data.refreshToken");

		mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void should_rejectOldTokens_when_accountLockedMidSession() throws Exception {
		User user = createUser("a@example.com", SystemRole.USER);
		String body = login("a@example.com", PASSWORD);
		String access = JsonPath.read(body, "$.data.accessToken");
		String refresh = JsonPath.read(body, "$.data.refreshToken");

		userService.lock(user.getId());

		mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(access)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void should_blockEverythingExceptPasswordChange_when_mustChangePassword() throws Exception {
		// Tài khoản mới tạo: must_change_password = true
		userService.create(new com.soudan.user.service.CreateUserCommand("new@example.com", "TempPass1234", "New", null,
				SystemRole.USER));
		String access = JsonPath.read(login("new@example.com", "TempPass1234"), "$.data.accessToken");

		mockMvc.perform(get("/api/v1/me/permissions").header("Authorization", bearer(access)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(access)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mustChangePassword").value(true));

		mockMvc.perform(post("/api/v1/me/change-password").header("Authorization", bearer(access))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"TempPass1234\",\"newPassword\":\"BrandNewPass99\"}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me/permissions").header("Authorization", bearer(access)))
				.andExpect(status().isOk());
	}
}

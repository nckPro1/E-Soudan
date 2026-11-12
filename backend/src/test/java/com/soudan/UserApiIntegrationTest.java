package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;

class UserApiIntegrationTest extends IntegrationTestBase {

	private static final String CREATE_BODY =
			"{\"email\":\"fresh@example.com\",\"temporaryPassword\":\"TempPass1234\",\"fullName\":\"Fresh\",\"position\":\"Dev\",\"systemRole\":\"USER\"}";

	// ---- Bảng phân quyền: endpoint x vai trò (anonymous = chưa đăng nhập) ----

	static Stream<Arguments> permissionTable() {
		String[] roles = { "ADMIN", "MANAGER", "USER", "ANONYMOUS" };
		// method, đường dẫn mẫu ({id} là tài khoản mục tiêu), body, status khi ADMIN gọi
		Object[][] endpoints = {
				{ HttpMethod.GET, "/api/v1/users", null, 200 },
				{ HttpMethod.GET, "/api/v1/users/{id}", null, 200 },
				{ HttpMethod.POST, "/api/v1/users", CREATE_BODY, 201 },
				{ HttpMethod.PUT, "/api/v1/users/{id}", "{\"fullName\":\"X\",\"position\":\"Y\",\"version\":0}", 200 },
				{ HttpMethod.PUT, "/api/v1/users/{id}/role", "{\"systemRole\":\"MANAGER\",\"version\":0}", 200 },
				{ HttpMethod.POST, "/api/v1/users/{id}/lock", null, 200 },
				{ HttpMethod.POST, "/api/v1/users/{id}/unlock", null, 200 },
		};
		Stream.Builder<Arguments> rows = Stream.builder();
		for (Object[] e : endpoints) {
			for (String role : roles) {
				int expected = switch (role) {
					case "ADMIN" -> (int) e[3];
					case "ANONYMOUS" -> 401;
					default -> 403;
				};
				rows.add(Arguments.of(role, e[0], e[1], e[2], expected));
			}
		}
		return rows.build();
	}

	@ParameterizedTest(name = "{0} {1} {2} -> {4}")
	@MethodSource("permissionTable")
	void should_matchPermissionTable_when_roleCallsEndpoint(String role, HttpMethod method, String path, String body,
			int expectedStatus) throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		createUser("manager@example.com", SystemRole.MANAGER);
		createUser("user@example.com", SystemRole.USER);
		User target = createUser("target@example.com", SystemRole.USER);

		MockHttpServletRequestBuilder request = MockMvcRequestBuilders
				.request(method, path.replace("{id}", target.getId().toString()));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body.replace("\"version\":0", "\"version\":" + target.getVersion()));
		}
		if (!"ANONYMOUS".equals(role)) {
			request.header("Authorization", bearer(accessTokenOf(role.toLowerCase() + "@example.com")));
		}

		mockMvc.perform(request).andExpect(status().is(expectedStatus));
	}

	// ---- Tạo tài khoản ----

	@Test
	void should_createUserWithoutPasswordInResponse_when_adminCreates() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(accessTokenOf("admin@example.com")))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.email").value("fresh@example.com"))
				.andExpect(jsonPath("$.data.systemRole").value("USER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.mustChangePassword").value(true))
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.data.temporaryPassword").doesNotExist());
	}

	@Test
	void should_return409_when_emailExistsInDifferentCase() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		createUser("fresh@example.com", SystemRole.USER);

		mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(accessTokenOf("admin@example.com")))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY.replace("fresh@", "FRESH@")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void should_return422_when_createBodyInvalid() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(accessTokenOf("admin@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"not-an-email\",\"temporaryPassword\":\"short\",\"fullName\":\"\",\"systemRole\":\"USER\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.length()").value(3));
	}

	@Test
	void should_return422_when_creatingAdminViaApi() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(accessTokenOf("admin@example.com")))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY.replace("\"USER\"", "\"ADMIN\"")))
				.andExpect(status().isUnprocessableContent());
	}

	// ---- Danh sách: phân trang, lọc, sắp xếp ----

	@Test
	void should_pageAndFilter_when_listing() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		createUser("alice@example.com", SystemRole.USER);
		createUser("bob@example.com", SystemRole.MANAGER);
		String token = bearer(accessTokenOf("admin@example.com"));

		mockMvc.perform(get("/api/v1/users").param("q", "ALICE").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalItems").value(1))
				.andExpect(jsonPath("$.data.items[0].email").value("alice@example.com"));

		mockMvc.perform(get("/api/v1/users").param("role", "manager").header("Authorization", token))
				.andExpect(jsonPath("$.data.totalItems").value(1));

		mockMvc.perform(get("/api/v1/users").param("size", "2").param("sort", "email,desc").header("Authorization", token))
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].email").value("bob@example.com"))
				.andExpect(jsonPath("$.data.totalItems").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2));
	}

	@Test
	void should_treatLikeWildcardsAsLiteral_when_searchContainsPercent() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		String token = bearer(accessTokenOf("admin@example.com"));

		mockMvc.perform(get("/api/v1/users").param("q", "%").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalItems").value(0));
	}

	@Test
	void should_return422_when_sizeOverLimit() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(get("/api/v1/users").param("size", "101")
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void should_return422InvalidSort_when_sortFieldNotWhitelisted() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(get("/api/v1/users").param("sort", "passwordHash")
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

	@Test
	void should_return422InvalidFilter_when_enumValueUnknown() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(get("/api/v1/users").param("role", "SUPERUSER")
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_FILTER"));
	}

	// ---- Khoá lạc quan, Admin cuối cùng, vai trò ----

	@Test
	void should_return409VersionConflict_when_updateWithStaleVersion() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		User target = createUser("target@example.com", SystemRole.USER);
		String token = bearer(accessTokenOf("admin@example.com"));
		long version = target.getVersion();

		mockMvc.perform(put("/api/v1/users/{id}", target.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"First\",\"position\":\"A\",\"version\":%d}".formatted(version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(version + 1));

		mockMvc.perform(put("/api/v1/users/{id}", target.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"Second\",\"position\":\"B\",\"version\":%d}".formatted(version)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	@Test
	void should_return409_when_lockingTheLastAdmin() throws Exception {
		User admin = createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(post("/api/v1/users/{id}/lock", admin.getId())
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
	}

	@Test
	void should_allowLockingAnAdmin_when_anotherActiveAdminExists() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		User second = createUser("admin2@example.com", SystemRole.ADMIN);

		mockMvc.perform(post("/api/v1/users/{id}/lock", second.getId())
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("LOCKED"));
	}

	@Test
	void should_return409_when_demotingTheLastAdmin() throws Exception {
		User admin = createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(put("/api/v1/users/{id}/role", admin.getId())
				.header("Authorization", bearer(accessTokenOf("admin@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"systemRole\":\"USER\",\"version\":%d}".formatted(admin.getVersion())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
	}

	@Test
	void should_return422_when_assigningAdminRoleViaApi() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		User target = createUser("target@example.com", SystemRole.USER);

		mockMvc.perform(put("/api/v1/users/{id}/role", target.getId())
				.header("Authorization", bearer(accessTokenOf("admin@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"systemRole\":\"ADMIN\",\"version\":%d}".formatted(target.getVersion())))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void should_return404_when_userNotFound() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);

		mockMvc.perform(get("/api/v1/users/{id}", 999999)
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	// ---- /me ----

	@Test
	void should_returnOnlyOwnPermissions_when_callingMePermissions() throws Exception {
		createUser("admin@example.com", SystemRole.ADMIN);
		createUser("user@example.com", SystemRole.USER);

		mockMvc.perform(get("/api/v1/me/permissions")
				.header("Authorization", bearer(accessTokenOf("user@example.com"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.systemRole").value("USER"))
				.andExpect(jsonPath("$.data.permissions.length()").value(0));

		mockMvc.perform(get("/api/v1/me/permissions")
				.header("Authorization", bearer(accessTokenOf("admin@example.com"))))
				.andExpect(jsonPath("$.data.systemRole").value("ADMIN"))
				.andExpect(jsonPath("$.data.permissions[?(@ == 'USER_CREATE')]").exists());
	}

	@Test
	void should_return422_when_currentPasswordWrong() throws Exception {
		createUser("user@example.com", SystemRole.USER);

		mockMvc.perform(post("/api/v1/me/change-password")
				.header("Authorization", bearer(accessTokenOf("user@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"BrandNewPass99\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
	}

	@Test
	void should_loginWithNewPassword_when_changePasswordSucceeds() throws Exception {
		createUser("user@example.com", SystemRole.USER);

		mockMvc.perform(post("/api/v1/me/change-password")
				.header("Authorization", bearer(accessTokenOf("user@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"%s\",\"newPassword\":\"BrandNewPass99\"}".formatted(PASSWORD)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@example.com\",\"password\":\"BrandNewPass99\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
				.andExpect(status().isUnauthorized());
	}
}

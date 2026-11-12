package com.soudan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;
import com.soudan.user.repository.UserRepository;
import com.soudan.user.service.CreateUserCommand;
import com.soudan.user.service.UserService;

/**
 * Nền cho test tích hợp: PostgreSQL thật (Testcontainers), cả chuỗi Security thật, MockMvc.
 * Mỗi test chạy trong một transaction và được huỷ (rollback) khi xong nên không ảnh hưởng nhau.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class IntegrationTestBase {

	protected static final String PASSWORD = "Passw0rd!123";

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected UserService userService;
	@Autowired
	protected UserRepository userRepository;
	@Autowired
	protected PasswordEncoder passwordEncoder;

	/** Tạo tài khoản đã đổi mật khẩu (must_change_password = false) để đăng nhập dùng được ngay. */
	protected User createUser(String email, SystemRole role) {
		User user = role == SystemRole.ADMIN
				? userService.createInitialAdmin(email, PASSWORD, "Admin " + email)
				: userService.create(new CreateUserCommand(email, PASSWORD, "User " + email, "Dev", role));
		user.setPassword(passwordEncoder.encode(PASSWORD), false);
		return userRepository.saveAndFlush(user);
	}

	protected String login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andReturn().getResponse().getContentAsString();
	}

	protected String accessTokenOf(String email) throws Exception {
		return JsonPath.read(login(email, PASSWORD), "$.data.accessToken");
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}
}

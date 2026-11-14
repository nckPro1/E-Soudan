package com.soudan.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tạo Admin đầu tiên khi khởi động (profile dev và prod), nếu hệ thống chưa có Admin nào. Không có lớp này ở production thì
 * không ai tạo được tài khoản. Chỉ chạy khi chưa có Admin nên khởi động lại không tạo thêm.
 * Email và mật khẩu tạm đến từ biến môi trường, không đặt cứng trong code; Admin phải đổi mật khẩu khi đăng nhập lần đầu.
 */
@Component
@Profile({ "dev", "prod" })
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserService userService;
	private final String email;
	private final String tempPassword;

	public AdminBootstrap(UserService userService,
			@Value("${app.bootstrap.admin-email:}") String email,
			@Value("${app.bootstrap.admin-temp-password:}") String tempPassword) {
		this.userService = userService;
		this.email = email;
		this.tempPassword = tempPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userService.hasAnyAdmin()) {
			return;
		}
		if (email.isBlank() || tempPassword.isBlank()) {
			log.warn("Chưa có Admin và chưa đặt ADMIN_EMAIL/ADMIN_TEMP_PASSWORD: không tạo Admin đầu tiên.");
			return;
		}
		userService.createInitialAdmin(email, tempPassword, "Administrator");
		log.info("Đã tạo Admin đầu tiên: {}", email);
	}
}

package com.soudan.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình tài khoản demo cho người xem sản phẩm tự thử nghiệp vụ ({@code app.demo.*}).
 *
 * @param enabled       bật tài khoản demo: tạo dữ liệu mẫu, công khai thông tin đăng nhập ở trang đăng nhập, chặn đổi mật khẩu.
 *                      Mặc định TẮT; môi trường thật không được bật
 * @param email         email của tài khoản demo (vai trò Thành viên)
 * @param password      mật khẩu công khai của tài khoản demo
 * @param seedOnStartup tạo/làm mới dữ liệu mẫu mỗi lần ứng dụng khởi động (tắt khi test để không để lại dữ liệu)
 */
@ConfigurationProperties("app.demo")
public record DemoProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("demo@soudan.local") String email,
		@DefaultValue("12345678") String password,
		@DefaultValue("true") boolean seedOnStartup) {
}

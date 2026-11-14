package com.soudan.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;

/**
 * Thông tin tài khoản demo cho trang đăng nhập (công khai, không cần đăng nhập). Chỉ trả email và mật khẩu khi demo được bật;
 * tắt thì {@code enabled=false} và không lộ gì. Mật khẩu này công khai có chủ ý, nên tài khoản demo bị chặn đổi mật khẩu.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

	public record DemoAccountResponse(boolean enabled, String email, String password) {
	}

	private final DemoProperties properties;

	public DemoController(DemoProperties properties) {
		this.properties = properties;
	}

	@GetMapping("/account")
	public ApiResponse<DemoAccountResponse> account() {
		return ApiResponse.ok(properties.enabled()
				? new DemoAccountResponse(true, properties.email(), properties.password())
				: new DemoAccountResponse(false, null, null));
	}
}

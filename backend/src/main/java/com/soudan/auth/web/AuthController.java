package com.soudan.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.auth.service.AuthService;
import com.soudan.auth.service.TokenPair;
import com.soudan.common.api.ApiResponse;

import jakarta.validation.Valid;

/** Đăng nhập, làm mới token, đăng xuất. Các đường dẫn này được phép không cần token (xem SecurityConfig). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.ok(toResponse(authService.login(request.email(), request.password())));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.ok(toResponse(authService.refresh(request.refreshToken())));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshRequest request) {
		authService.logout(request.refreshToken());
	}

	private static TokenResponse toResponse(TokenPair pair) {
		return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds(),
				pair.mustChangePassword());
	}
}

package com.soudan.user.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.AuthenticatedUser;
import com.soudan.common.security.CurrentUser;
import com.soudan.user.mapper.UserMapper;
import com.soudan.user.service.UserService;

import jakarta.validation.Valid;

/** Thông tin và thao tác của chính người đăng nhập. Ai đăng nhập cũng gọi được. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	private final CurrentUser currentUser;
	private final UserService userService;
	private final UserMapper mapper;
	private final AccessPolicy accessPolicy;

	public MeController(CurrentUser currentUser, UserService userService, UserMapper mapper, AccessPolicy accessPolicy) {
		this.currentUser = currentUser;
		this.userService = userService;
		this.mapper = mapper;
		this.accessPolicy = accessPolicy;
	}

	@GetMapping
	public ApiResponse<UserResponse> me() {
		return ApiResponse.ok(mapper.toResponse(userService.get(currentUser.require().id())));
	}

	@GetMapping("/permissions")
	public ApiResponse<PermissionsResponse> permissions() {
		AuthenticatedUser user = currentUser.require();
		return ApiResponse.ok(new PermissionsResponse(user.role(), accessPolicy.permissionsOf(user.role())));
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(currentUser.require().id(), request.currentPassword(), request.newPassword());
	}
}

package com.soudan.user.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.api.PageQuery;
import com.soudan.common.api.PageResponse;
import com.soudan.user.mapper.UserMapper;
import com.soudan.user.service.CreateUserCommand;
import com.soudan.user.service.UserService;

import jakarta.validation.Valid;

/** Quản lý tài khoản. Controller mỏng: chỉ gọi service và ánh xạ sang DTO. Chỉ ADMIN (kiểm tra ở backend). */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("@accessPolicy.isAdmin()")
public class UserController {

	private final UserService userService;
	private final UserMapper mapper;

	public UserController(UserService userService, UserMapper mapper) {
		this.userService = userService;
		this.mapper = mapper;
	}

	@GetMapping
	@PreAuthorize("@accessPolicy.canReadUsers()")
	public ApiResponse<PageResponse<UserResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String role) {
		Map<String, String> filters = new HashMap<>();
		putIfPresent(filters, "q", q);
		putIfPresent(filters, "status", status);
		putIfPresent(filters, "role", role);
		PageQuery query = new PageQuery(page, size, sort, filters);
		return ApiResponse.ok(PageResponse.of(userService.search(query), mapper::toResponse));
	}

	/** Danh sách rút gọn để chọn người (thành viên nhóm...). Chỉ người đang hoạt động, chỉ id, tên, vị trí. */
	@GetMapping("/options")
	@PreAuthorize("@accessPolicy.canReadUsers()")
	public ApiResponse<PageResponse<UserOptionResponse>> options(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PageQuery.MAX_SIZE) int size,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String role) {
		Map<String, String> filters = new HashMap<>();
		filters.put("status", "ACTIVE");
		putIfPresent(filters, "q", q);
		putIfPresent(filters, "role", role);
		PageQuery query = new PageQuery(page, size, "fullName", filters);
		return ApiResponse.ok(PageResponse.of(userService.search(query), mapper::toOption));
	}

	@GetMapping("/{id}")
	@PreAuthorize("@accessPolicy.canReadUsers()")
	public ApiResponse<UserResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(mapper.toResponse(userService.get(id)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		var user = userService.create(new CreateUserCommand(request.email(), request.temporaryPassword(),
				request.fullName(), request.position(), request.systemRole()));
		return ApiResponse.ok(mapper.toResponse(user));
	}

	@PutMapping("/{id}")
	public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		var user = userService.updateProfile(id, request.version(), request.fullName(), request.position());
		return ApiResponse.ok(mapper.toResponse(user));
	}

	@PutMapping("/{id}/role")
	public ApiResponse<UserResponse> changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
		var user = userService.changeRole(id, request.version(), request.systemRole());
		return ApiResponse.ok(mapper.toResponse(user));
	}

	@PostMapping("/{id}/lock")
	public ApiResponse<UserResponse> lock(@PathVariable Long id) {
		return ApiResponse.ok(mapper.toResponse(userService.lock(id)));
	}

	@PostMapping("/{id}/unlock")
	public ApiResponse<UserResponse> unlock(@PathVariable Long id) {
		return ApiResponse.ok(mapper.toResponse(userService.unlock(id)));
	}

	private static void putIfPresent(Map<String, String> filters, String key, String value) {
		if (value != null && !value.isBlank()) {
			filters.put(key, value);
		}
	}
}

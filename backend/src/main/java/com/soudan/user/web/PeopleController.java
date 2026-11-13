package com.soudan.user.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;
import com.soudan.user.service.UserService;

/**
 * Danh bạ và hồ sơ công khai trong công ty, để bấm vào một người rồi nhắn tin. Ai đăng nhập cũng xem được; chỉ thấy
 * thông tin hiển thị (tên, vị trí, email, vai trò), không thấy trạng thái tài khoản. Admin và tài khoản bị khoá không có
 * trong danh bạ.
 */
@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {

	public record PersonResponse(Long id, String fullName, String position, String email, SystemRole systemRole) {
		static PersonResponse of(User u) {
			return new PersonResponse(u.getId(), u.getFullName(), u.getPosition(), u.getEmail(), u.getSystemRole());
		}
	}

	private final UserService users;

	public PeopleController(UserService users) {
		this.users = users;
	}

	@GetMapping
	public ApiResponse<List<PersonResponse>> search(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(users.searchPeople(q, size).stream().map(PersonResponse::of).toList());
	}

	@GetMapping("/{id}")
	public ApiResponse<PersonResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(PersonResponse.of(users.getPerson(id)));
	}
}

package com.soudan.team.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.soudan.team.mapper.TeamMapper;
import com.soudan.team.service.CreateTeamCommand;
import com.soudan.team.service.TeamService;

import jakarta.validation.Valid;

/**
 * Quản lý nhóm. Tạo, sửa, lưu trữ và quản lý thành viên chỉ Admin. Danh sách tự lọc theo người xem
 * (Admin thấy hết, người khác chỉ thấy nhóm mình có mặt); xem chi tiết cần là Admin hoặc có mặt trong nhóm.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

	private final TeamService teamService;
	private final TeamMapper mapper;

	public TeamController(TeamService teamService, TeamMapper mapper) {
		this.teamService = teamService;
		this.mapper = mapper;
	}

	@GetMapping
	public ApiResponse<PageResponse<TeamResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String status) {
		Map<String, String> filters = new HashMap<>();
		putIfPresent(filters, "q", q);
		putIfPresent(filters, "status", status);
		PageQuery query = new PageQuery(page, size, sort, filters);
		return ApiResponse.ok(PageResponse.of(teamService.search(query), mapper::toResponse));
	}

	@GetMapping("/{teamId}")
	@PreAuthorize("@accessPolicy.canViewTeam(#teamId)")
	public ApiResponse<TeamDetailResponse> get(@PathVariable Long teamId) {
		return ApiResponse.ok(mapper.toResponse(teamService.get(teamId)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@accessPolicy.isAdmin()")
	public ApiResponse<TeamDetailResponse> create(@Valid @RequestBody CreateTeamRequest request) {
		var detail = teamService.create(new CreateTeamCommand(request.name(), request.description(),
				request.leaderIds(), orEmpty(request.memberIds()), orEmpty(request.observerIds())));
		return ApiResponse.ok(mapper.toResponse(detail));
	}

	@PutMapping("/{teamId}")
	@PreAuthorize("@accessPolicy.isAdmin()")
	public ApiResponse<TeamDetailResponse> update(@PathVariable Long teamId,
			@Valid @RequestBody UpdateTeamRequest request) {
		return ApiResponse.ok(mapper.toResponse(
				teamService.update(teamId, request.version(), request.name(), request.description())));
	}

	@PostMapping("/{teamId}/archive")
	@PreAuthorize("@accessPolicy.isAdmin()")
	public ApiResponse<TeamDetailResponse> archive(@PathVariable Long teamId,
			@Valid @RequestBody ArchiveTeamRequest request) {
		return ApiResponse.ok(mapper.toResponse(teamService.archive(teamId, request.version())));
	}

	@PostMapping("/{teamId}/members")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@accessPolicy.isAdmin()")
	public ApiResponse<TeamDetailResponse> addMember(@PathVariable Long teamId,
			@Valid @RequestBody AddMemberRequest request) {
		return ApiResponse.ok(mapper.toResponse(teamService.addMember(teamId, request.userId(), request.teamRole())));
	}

	@PutMapping("/{teamId}/members/{userId}")
	@PreAuthorize("@accessPolicy.isAdmin()")
	public ApiResponse<TeamDetailResponse> changeMemberRole(@PathVariable Long teamId, @PathVariable Long userId,
			@Valid @RequestBody ChangeMemberRoleRequest request) {
		return ApiResponse.ok(mapper.toResponse(teamService.changeMemberRole(teamId, userId, request.teamRole())));
	}

	@DeleteMapping("/{teamId}/members/{userId}")
	@PreAuthorize("@accessPolicy.isAdmin()")
	public ApiResponse<TeamDetailResponse> removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
		return ApiResponse.ok(mapper.toResponse(teamService.removeMember(teamId, userId)));
	}

	private static List<Long> orEmpty(List<Long> ids) {
		return ids == null ? List.of() : ids;
	}

	private static void putIfPresent(Map<String, String> filters, String key, String value) {
		if (value != null && !value.isBlank()) {
			filters.put(key, value);
		}
	}
}

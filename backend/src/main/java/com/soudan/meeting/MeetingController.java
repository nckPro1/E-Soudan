package com.soudan.meeting;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.security.AuthenticatedUser;
import com.soudan.common.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;

/**
 * Họp video. {@code POST /meetings} (cần đăng nhập) tạo phòng và trả token chủ trì; các endpoint {@code /public/meetings}
 * cho khách (không cần tài khoản) biết cấu hình và lấy token khách. Khách vào phòng chờ cho tới khi chủ trì duyệt.
 */
@RestController
@RequestMapping("/api/v1")
public class MeetingController {

	public record ConfigResponse(boolean enabled, String appId, String domain) {
	}

	public record CreateRequest(@Size(max = 100) String displayName) {
	}

	public record JoinRequest(@Size(max = 100) String name) {
	}

	public record MeetingResponse(String room, String appId, String domain, String jwt) {
	}

	private static final int GUEST_LIMIT = 30;
	private static final Duration GUEST_WINDOW = Duration.ofMinutes(1);

	private final MeetingProperties properties;
	private final MeetingTokenService tokens;
	private final CurrentUser currentUser;
	/** Giới hạn số lần lấy token khách theo địa chỉ IP, tránh bị lợi dụng để tạo phòng hàng loạt. */
	private final Map<String, Deque<Long>> guestHits = new ConcurrentHashMap<>();

	public MeetingController(MeetingProperties properties, MeetingTokenService tokens, CurrentUser currentUser) {
		this.properties = properties;
		this.tokens = tokens;
		this.currentUser = currentUser;
	}

	@GetMapping("/public/meetings/config")
	public ApiResponse<ConfigResponse> config() {
		boolean on = tokens.configured();
		return ApiResponse.ok(new ConfigResponse(on, on ? properties.appId() : null, on ? properties.domain() : null));
	}

	@PostMapping("/meetings")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MeetingResponse> create(@RequestBody(required = false) CreateRequest request) {
		AuthenticatedUser user = currentUser.require();
		String room = tokens.newRoom();
		String jwt = tokens.sign(room, request == null ? null : request.displayName(), user.email(), true);
		return ApiResponse.ok(new MeetingResponse(room, properties.appId(), properties.domain(), jwt));
	}

	@PostMapping("/public/meetings/{room}/join")
	public ApiResponse<MeetingResponse> join(@PathVariable String room, @RequestBody(required = false) JoinRequest request,
			HttpServletRequest http) {
		if (!MeetingTokenService.validRoom(room)) {
			throw new BusinessException(ErrorCode.INVALID_MEETING_ROOM);
		}
		throttle(http.getRemoteAddr());
		String jwt = tokens.sign(room, request == null ? null : request.name(), null, false);
		return ApiResponse.ok(new MeetingResponse(room, properties.appId(), properties.domain(), jwt));
	}

	private void throttle(String ip) {
		long now = System.currentTimeMillis();
		if (guestHits.size() > 10_000) {
			guestHits.clear();
		}
		Deque<Long> hits = guestHits.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
		synchronized (hits) {
			while (!hits.isEmpty() && now - hits.peekFirst() > GUEST_WINDOW.toMillis()) {
				hits.pollFirst();
			}
			if (hits.size() >= GUEST_LIMIT) {
				throw new BusinessException(ErrorCode.MEETING_RATE_LIMITED);
			}
			hits.addLast(now);
		}
	}
}

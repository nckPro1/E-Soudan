package com.soudan.notification.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.api.PageResponse;
import com.soudan.notification.domain.NotificationType;
import com.soudan.notification.service.NotificationService;
import com.soudan.notification.service.NotificationService.NotificationView;

/** Thông báo của người đang đăng nhập. Mỗi người chỉ thấy và thao tác được thông báo của chính mình. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	public record PersonResponse(Long id, String fullName) {
	}

	public record NotificationResponse(Long id, NotificationType type, PersonResponse actor, Long taskId,
			Long communityId, Long postId, Long postAuthorId, Long conversationId, String subject, String snippet,
			Instant createdAt, boolean read) {
		static NotificationResponse of(NotificationView v) {
			return new NotificationResponse(v.id(), v.type(),
					v.actor() == null ? null : new PersonResponse(v.actor().id(), v.actor().fullName()), v.taskId(),
					v.communityId(), v.postId(), v.postAuthorId(), v.conversationId(), v.subject(), v.snippet(), v.createdAt(),
					v.read());
		}
	}

	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	/** Mới nhất trước. {@code unread=true} chỉ lấy thông báo chưa đọc. */
	@GetMapping
	public ApiResponse<PageResponse<NotificationResponse>> list(@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(PageResponse.of(service.list(unread, page, size), NotificationResponse::of));
	}

	@GetMapping("/unread-count")
	public ApiResponse<Map<String, Long>> unreadCount() {
		return ApiResponse.ok(Map.of("count", service.unreadCount()));
	}

	@PostMapping("/{id}/read")
	public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
		return ApiResponse.ok(NotificationResponse.of(service.markRead(id)));
	}

	@PostMapping("/read-all")
	public ApiResponse<Map<String, Integer>> markAllRead() {
		return ApiResponse.ok(Map.of("updated", service.markAllRead()));
	}
}

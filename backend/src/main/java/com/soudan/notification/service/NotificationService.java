package com.soudan.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.exception.NotFoundException;
import com.soudan.common.security.CurrentUser;
import com.soudan.notification.domain.Notification;
import com.soudan.notification.domain.NotificationType;
import com.soudan.notification.repository.NotificationRepository;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Hộp thông báo của người đang đăng nhập (mục "Hoạt động"): xem danh sách, đếm chưa đọc, đánh dấu đã đọc.
 * Mỗi người chỉ thấy và thao tác được thông báo của chính mình; thông báo của người khác coi như không tồn tại (404).
 */
@Service
@Transactional
public class NotificationService {

	public static final int MAX_PAGE_SIZE = 50;

	public record PersonView(Long id, String fullName) {
	}

	public record NotificationView(Long id, NotificationType type, PersonView actor, Long taskId, Long communityId,
			Long postId, Long postAuthorId, Long conversationId, String subject, String snippet, Instant createdAt,
			boolean read) {
	}

	private final NotificationRepository notifications;
	private final UserApi users;
	private final CurrentUser currentUser;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, UserApi users, CurrentUser currentUser,
			Clock clock) {
		this.notifications = notifications;
		this.users = users;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<NotificationView> list(boolean unreadOnly, int page, int size) {
		Long me = currentUser.require().id();
		PageRequest paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		Page<Notification> rows = unreadOnly ? notifications.findUnreadOf(me, paging) : notifications.findAllOf(me, paging);
		Set<Long> actorIds = new HashSet<>();
		rows.forEach(n -> {
			if (n.getActorId() != null) {
				actorIds.add(n.getActorId());
			}
		});
		Map<Long, PersonView> actors = users.findSummariesByIds(actorIds).stream()
				.collect(Collectors.toMap(UserSummary::id, u -> new PersonView(u.id(), u.fullName())));
		return rows.map(n -> view(n, actors.get(n.getActorId())));
	}

	@Transactional(readOnly = true)
	public long unreadCount() {
		return notifications.countByRecipientIdAndReadAtIsNull(currentUser.require().id());
	}

	/** Đánh dấu một thông báo của mình là đã đọc. Đánh dấu lại thì không đổi gì. */
	public NotificationView markRead(Long id) {
		Notification n = notifications.findByIdAndRecipientId(id, currentUser.require().id())
				.orElseThrow(() -> new NotFoundException("Không tìm thấy thông báo " + id));
		n.markRead(clock.instant());
		notifications.saveAndFlush(n);
		PersonView actor = n.getActorId() == null ? null
				: users.findSummariesByIds(Set.of(n.getActorId())).stream().findFirst()
						.map(u -> new PersonView(u.id(), u.fullName())).orElse(null);
		return view(n, actor);
	}

	/** Đánh dấu mọi thông báo của mình là đã đọc; trả về số thông báo vừa đổi. */
	public int markAllRead() {
		return notifications.markAllRead(currentUser.require().id(), clock.instant());
	}

	private static NotificationView view(Notification n, PersonView actor) {
		return new NotificationView(n.getId(), n.getType(), actor, n.getTaskId(), n.getCommunityId(), n.getPostId(),
				n.getPostAuthorId(), n.getConversationId(), n.getSubject(), n.getSnippet(), n.getCreatedAt(), n.isRead());
	}
}

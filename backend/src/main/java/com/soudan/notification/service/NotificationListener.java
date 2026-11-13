package com.soudan.notification.service;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.soudan.chat.ChatRequestAcceptedEvent;
import com.soudan.chat.ChatRequestedEvent;
import com.soudan.notification.domain.Notification;
import com.soudan.notification.domain.NotificationType;
import com.soudan.notification.repository.NotificationRepository;
import com.soudan.report.ReportCommentedEvent;
import com.soudan.report.ReportPostedEvent;
import com.soudan.report.ReportReactedEvent;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskCommentReactedEvent;
import com.soudan.task.TaskCommentedEvent;
import com.soudan.task.TaskReviewedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.team.TeamApi;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Biến các sự kiện của task và báo cáo thành thông báo trong ứng dụng. Chạy sau khi giao dịch của người gây ra sự
 * kiện đã commit, trên luồng riêng. Quy tắc chung: không bao giờ báo cho chính người gây ra sự kiện, chỉ báo người
 * đang hoạt động, và mỗi người nhiều nhất một thông báo cho mỗi sự kiện.
 */
@Component
class NotificationListener {

	private static final int EXCERPT = 200;

	private final NotificationRepository notifications;
	private final UserApi users;
	private final TeamApi teams;
	private final Clock clock;

	NotificationListener(NotificationRepository notifications, UserApi users, TeamApi teams, Clock clock) {
		this.notifications = notifications;
		this.users = users;
		this.teams = teams;
		this.clock = clock;
	}

	// ---- task ----

	@ApplicationModuleListener
	void on(TaskAssignedEvent event) {
		forTask(Set.of(event.assigneeId()), event.assignedById(), NotificationType.TASK_ASSIGNED, event.taskId(),
				event.title(), null);
	}

	@ApplicationModuleListener
	void on(TaskSubmittedForReviewEvent event) {
		forTask(new LinkedHashSet<>(teams.findLeaderIds(event.teamId())), event.assigneeId(),
				NotificationType.TASK_SUBMITTED, event.taskId(), event.title(), null);
	}

	@ApplicationModuleListener
	void on(TaskReviewedEvent event) {
		forTask(Set.of(event.assigneeId()), event.reviewerId(),
				event.approved() ? NotificationType.TASK_APPROVED : NotificationType.TASK_REVISION, event.taskId(),
				event.title(), event.reason());
	}

	/** Người được trả lời nhận "trả lời bình luận của bạn"; người phụ trách và các Leader nhận "bình luận mới". */
	@ApplicationModuleListener
	void on(TaskCommentedEvent event) {
		String excerpt = excerpt(event.body());
		if (event.repliedToAuthorId() != null) {
			forTask(Set.of(event.repliedToAuthorId()), event.authorId(), NotificationType.TASK_REPLY, event.taskId(),
					event.title(), excerpt);
		}
		Set<Long> others = new LinkedHashSet<>();
		others.add(event.assigneeId());
		others.addAll(teams.findLeaderIds(event.teamId()));
		others.remove(event.repliedToAuthorId());
		forTask(others, event.authorId(), NotificationType.TASK_COMMENT, event.taskId(), event.title(), excerpt);
	}

	@ApplicationModuleListener
	void on(TaskCommentReactedEvent event) {
		forTask(Set.of(event.commentAuthorId()), event.actorId(), NotificationType.TASK_REACTION, event.taskId(),
				event.title(), event.emoji());
	}

	// ---- báo cáo ----

	@ApplicationModuleListener
	void on(ReportPostedEvent event) {
		forReport(Set.of(event.ownerId()), event.authorId(), NotificationType.REPORT_POSTED, event.communityId(),
				event.postId(), event.authorId(), event.title(), null);
	}

	/** Tác giả được trả lời nhận "trả lời"; chủ báo cáo nhận "bình luận" (nếu chưa nhận vì là người được trả lời). */
	@ApplicationModuleListener
	void on(ReportCommentedEvent event) {
		String excerpt = excerpt(event.body());
		if (event.repliedToAuthorId() != null) {
			forReport(Set.of(event.repliedToAuthorId()), event.authorId(), NotificationType.REPORT_REPLY,
					event.communityId(), event.postId(), event.postAuthorId(), event.postTitle(), excerpt);
		}
		if (!event.postAuthorId().equals(event.repliedToAuthorId())) {
			forReport(Set.of(event.postAuthorId()), event.authorId(), NotificationType.REPORT_COMMENT,
					event.communityId(), event.postId(), event.postAuthorId(), event.postTitle(), excerpt);
		}
	}

	@ApplicationModuleListener
	void on(ReportReactedEvent event) {
		forReport(Set.of(event.targetAuthorId()), event.actorId(),
				event.onComment() ? NotificationType.REPORT_COMMENT_REACTION : NotificationType.REPORT_REACTION,
				event.communityId(), event.postId(), event.postAuthorId(), event.postTitle(), event.emoji());
	}

	// ---- trò chuyện ----

	@ApplicationModuleListener
	void on(ChatRequestedEvent event) {
		forChat(Set.of(event.recipientId()), event.requesterId(), NotificationType.CHAT_REQUEST, event.conversationId());
	}

	@ApplicationModuleListener
	void on(ChatRequestAcceptedEvent event) {
		forChat(Set.of(event.requesterId()), event.accepterId(), NotificationType.CHAT_REQUEST_ACCEPTED,
				event.conversationId());
	}

	// ---- nội bộ ----

	private void forChat(Collection<Long> recipients, Long actorId, NotificationType type, Long conversationId) {
		for (Long recipient : deliverable(recipients, actorId)) {
			notifications.save(Notification.forChat(recipient, type, actorId, conversationId, clock.instant()));
		}
	}

	private void forTask(Collection<Long> recipients, Long actorId, NotificationType type, Long taskId, String subject,
			String snippet) {
		for (Long recipient : deliverable(recipients, actorId)) {
			notifications.save(Notification.forTask(recipient, type, actorId, taskId, subject, snippet, clock.instant()));
		}
	}

	private void forReport(Collection<Long> recipients, Long actorId, NotificationType type, Long communityId,
			Long postId, Long postAuthorId, String subject, String snippet) {
		for (Long recipient : deliverable(recipients, actorId)) {
			notifications.save(Notification.forReport(recipient, type, actorId, communityId, postId, postAuthorId,
					subject, snippet, clock.instant()));
		}
	}

	/** Bỏ người gây ra sự kiện và người không còn hoạt động. */
	private List<Long> deliverable(Collection<Long> recipients, Long actorId) {
		Set<Long> wanted = new LinkedHashSet<>(recipients);
		wanted.remove(actorId);
		if (wanted.isEmpty()) {
			return List.of();
		}
		return users.findSummariesByIds(wanted).stream().filter(UserSummary::active).map(UserSummary::id)
				.collect(Collectors.toList());
	}

	private static String excerpt(String body) {
		if (body == null) {
			return null;
		}
		String text = body.strip().replaceAll("\\s+", " ");
		return text.length() <= EXCERPT ? text : text.substring(0, EXCERPT - 1) + "…";
	}
}

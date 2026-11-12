package com.soudan.notification.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.soudan.notification.service.MailContent.Row;
import com.soudan.notification.service.MailContent.Tone;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskCommentedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Gửi email khi: (1) task được giao cho Member, (2) Member đánh dấu xong và chờ duyệt (gửi cho các Leader của nhóm),
 * (3) có bình luận mới. Chạy sau khi giao dịch của người gọi đã commit, trên luồng riêng.
 */
@Component
class TaskMailListener {

	private static final Logger log = LoggerFactory.getLogger(TaskMailListener.class);
	private static final int EXCERPT_LENGTH = 500;

	private final UserApi users;
	private final TeamApi teams;
	private final EmailService email;
	private final NotificationProperties properties;

	TaskMailListener(UserApi users, TeamApi teams, EmailService email, NotificationProperties properties) {
		this.users = users;
		this.teams = teams;
		this.email = email;
		this.properties = properties;
	}

	@ApplicationModuleListener
	void on(TaskAssignedEvent event) {
		Optional<UserSummary> assignee = user(event.assigneeId());
		if (assignee.isEmpty() || !assignee.get().active()) {
			return;
		}
		String assigner = user(event.assignedById()).map(UserSummary::fullName).orElse("Leader");
		String due = event.dueDate() == null ? "Không có" : event.dueDate().toString();
		MailContent content = new MailContent(
				"[E-Soudan] Bạn được giao việc mới: " + event.title(),
				assigner + " vừa giao cho bạn một công việc trong nhóm " + teamName(event.teamId()),
				"Được giao", Tone.INFO,
				"Bạn được giao việc mới",
				assignee.get().fullName(),
				assigner + " vừa giao cho bạn một công việc mới. Hãy xem chi tiết và bắt đầu khi bạn sẵn sàng.",
				List.of(new Row("Công việc", event.title()),
						new Row("Nhóm", teamName(event.teamId())),
						new Row("Giao bởi", assigner),
						new Row("Hạn chót", due)),
				null, "Mở công việc", link(event.taskId()));
		email.send(assignee.get().email(), content);
	}

	@ApplicationModuleListener
	void on(TaskSubmittedForReviewEvent event) {
		String assignee = user(event.assigneeId()).map(UserSummary::fullName).orElse("Thành viên");
		String team = teamName(event.teamId());
		List<UserSummary> leaders = users.findSummariesByIds(teams.findLeaderIds(event.teamId()));
		RuntimeException failure = null;
		for (UserSummary leader : leaders) {
			if (!leader.active()) {
				continue;
			}
			MailContent content = new MailContent(
					"[E-Soudan] Cần duyệt: " + event.title(),
					assignee + " đã hoàn thành và đang chờ bạn duyệt",
					"Chờ duyệt", Tone.WARNING,
					"Có công việc đang chờ bạn duyệt",
					leader.fullName(),
					assignee + " đã đánh dấu hoàn thành và đang chờ bạn duyệt. Bạn có thể duyệt hoặc trả lại kèm lý do.",
					List.of(new Row("Công việc", event.title()),
							new Row("Nhóm", team),
							new Row("Người thực hiện", assignee)),
					null, "Xem và duyệt", link(event.taskId()));
			failure = send(leader, content, failure);
		}
		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * Báo bình luận mới cho người phụ trách và các Leader; riêng tác giả của bình luận được trả lời thì nhận
	 * thư "trả lời bình luận của bạn". Người viết không nhận thư của chính mình; mỗi người nhiều nhất một thư.
	 */
	@ApplicationModuleListener
	void on(TaskCommentedEvent event) {
		Set<Long> recipients = new LinkedHashSet<>();
		if (event.repliedToAuthorId() != null) {
			recipients.add(event.repliedToAuthorId());
		}
		recipients.add(event.assigneeId());
		recipients.addAll(teams.findLeaderIds(event.teamId()));
		recipients.remove(event.authorId());
		if (recipients.isEmpty()) {
			return;
		}

		String author = user(event.authorId()).map(UserSummary::fullName).orElse("Một thành viên");
		String team = teamName(event.teamId());
		String excerpt = excerpt(event.body());
		RuntimeException failure = null;
		for (UserSummary recipient : users.findSummariesByIds(recipients)) {
			if (!recipient.active()) {
				continue;
			}
			boolean directReply = recipient.id().equals(event.repliedToAuthorId());
			MailContent content = new MailContent(
					directReply
							? "[E-Soudan] " + author + " đã trả lời bình luận của bạn: " + event.title()
							: "[E-Soudan] Bình luận mới: " + event.title(),
					author + (directReply ? " đã trả lời bình luận của bạn" : " đã bình luận") + ": " + excerpt,
					directReply ? "Trả lời bình luận của bạn" : "Bình luận mới", Tone.INFO,
					directReply ? author + " đã trả lời bạn" : author + " đã bình luận",
					recipient.fullName(),
					author + (directReply ? " đã trả lời bình luận của bạn" : " đã bình luận")
							+ " trong công việc bên dưới. Mở công việc để xem toàn bộ cuộc trao đổi và trả lời.",
					List.of(new Row("Công việc", event.title()), new Row("Nhóm", team)),
					excerpt, "Xem và trả lời", link(event.taskId()));
			failure = send(recipient, content, failure);
		}
		if (failure != null) {
			throw failure;
		}
	}

	/** Gửi cho một người; lỗi được ghi lại và trả về để các người còn lại vẫn được gửi, rồi ném lỗi sau cùng. */
	private RuntimeException send(UserSummary recipient, MailContent content, RuntimeException previous) {
		try {
			email.send(recipient.email(), content);
			return previous;
		} catch (RuntimeException e) {
			log.warn("Gửi email tới {} thất bại", recipient.email(), e);
			return e;
		}
	}

	/** Trích tối đa {@value #EXCERPT_LENGTH} ký tự. */
	static String excerpt(String text) {
		return text.length() > EXCERPT_LENGTH ? text.substring(0, EXCERPT_LENGTH) + "…" : text;
	}

	private Optional<UserSummary> user(Long id) {
		return users.findSummariesByIds(List.of(id)).stream().findFirst();
	}

	private String teamName(Long teamId) {
		return teams.findTeam(teamId).map(TeamInfo::name).orElse("");
	}

	private String link(Long taskId) {
		return properties.frontendUrl().replaceAll("/+$", "") + "/tasks/" + taskId;
	}
}

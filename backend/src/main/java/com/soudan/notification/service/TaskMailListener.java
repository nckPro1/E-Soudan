package com.soudan.notification.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Gửi email khi: (1) task được giao cho Member, (2) Member đánh dấu xong và chờ duyệt (gửi cho các Leader của nhóm).
 * Chạy sau khi giao dịch của người gọi đã commit, trên luồng riêng.
 */
@Component
class TaskMailListener {

	private static final Logger log = LoggerFactory.getLogger(TaskMailListener.class);

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
		String due = event.dueDate() == null ? "không có" : event.dueDate().toString();
		String body = """
				Xin chào %s,

				%s vừa giao cho bạn một công việc mới trong nhóm %s.

				Tiêu đề: %s
				Hạn chót: %s

				Mở công việc: %s

				-- E-Soudan
				""".formatted(assignee.get().fullName(), assigner, teamName(event.teamId()), event.title(), due,
				link(event.taskId()));
		email.send(assignee.get().email(), "[E-Soudan] Bạn được giao việc mới: " + event.title(), body);
	}

	@ApplicationModuleListener
	void on(TaskSubmittedForReviewEvent event) {
		String assignee = user(event.assigneeId()).map(UserSummary::fullName).orElse("Thành viên");
		List<UserSummary> leaders = users.findSummariesByIds(teams.findLeaderIds(event.teamId()));
		RuntimeException failure = null;
		for (UserSummary leader : leaders) {
			if (!leader.active()) {
				continue;
			}
			String body = """
					Xin chào %s,

					%s đã đánh dấu hoàn thành và đang chờ bạn duyệt công việc "%s" của nhóm %s.

					Mở công việc để duyệt hoặc trả lại: %s

					-- E-Soudan
					""".formatted(leader.fullName(), assignee, event.title(), teamName(event.teamId()),
					link(event.taskId()));
			try {
				email.send(leader.email(), "[E-Soudan] Cần duyệt: " + event.title(), body);
			} catch (RuntimeException e) {
				// Một Leader lỗi không chặn các Leader còn lại; lỗi được ném sau cùng để sự kiện được giữ lại và gửi lại.
				log.warn("Gửi email tới {} thất bại", leader.email(), e);
				failure = e;
			}
		}
		if (failure != null) {
			throw failure;
		}
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

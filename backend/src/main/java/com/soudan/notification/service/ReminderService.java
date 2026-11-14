package com.soudan.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.notification.domain.Notification;
import com.soudan.notification.domain.NotificationType;
import com.soudan.notification.repository.NotificationRepository;
import com.soudan.report.ReportApi;
import com.soudan.report.ReportApi.MissingReports;
import com.soudan.task.TaskApi;
import com.soudan.task.TaskApi.DueTask;
import com.soudan.team.TeamApi;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Nhắc việc và nhắc nộp báo cáo (thông báo trong ứng dụng). Hệ thống tự gửi, không có người gây ra (actor rỗng).
 * <ul>
 * <li>Việc đến hạn hôm nay hoặc ngày mai: nhắc người phụ trách.</li>
 * <li>Việc quá hạn một ngày: báo người phụ trách và các Leader của nhóm.</li>
 * <li>Ngày làm việc: nhắc những thành viên chưa nộp báo cáo (bỏ người nghỉ phép và người đã bị khoá).</li>
 * </ul>
 * Mỗi nhắc chỉ gửi một lần mỗi ngày cho mỗi người và mỗi đối tượng, nên chạy lại (khởi động lại, nhiều máy) không gửi trùng.
 * Người bị khoá không nhận nhắc.
 */
@Service
public class ReminderService {

	private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

	private final TaskApi tasks;
	private final ReportApi reports;
	private final TeamApi teams;
	private final UserApi users;
	private final NotificationRepository notifications;
	private final Clock clock;
	private final ZoneId zone;

	public ReminderService(TaskApi tasks, ReportApi reports, TeamApi teams, UserApi users,
			NotificationRepository notifications, Clock clock, ZoneId zone) {
		this.tasks = tasks;
		this.reports = reports;
		this.teams = teams;
		this.users = users;
		this.notifications = notifications;
		this.clock = clock;
		this.zone = zone;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	/** Nhắc hạn việc cho ngày {@code today}. Trả về số thông báo đã tạo. */
	@Transactional
	public int remindTasks(LocalDate today) {
		List<DueTask> due = tasks.openTasksDueBetween(today.minusDays(1), today.plusDays(1));
		if (due.isEmpty()) {
			return 0;
		}
		Instant startOfDay = startOfRealToday();
		Instant now = Instant.now(clock);
		Set<Long> candidates = new HashSet<>();
		due.forEach(t -> candidates.add(t.assigneeId()));
		Set<Long> active = activeAmong(candidates);
		int created = 0;
		for (DueTask t : due) {
			boolean overdue = t.dueDate().isBefore(today);
			NotificationType type = overdue ? NotificationType.TASK_OVERDUE : NotificationType.TASK_DUE_SOON;
			Set<Long> recipients = new LinkedHashSet<>();
			if (active.contains(t.assigneeId())) {
				recipients.add(t.assigneeId());
			}
			if (overdue) {
				recipients.addAll(activeAmong(new HashSet<>(teams.findLeaderIds(t.teamId()))));
			}
			for (Long recipient : recipients) {
				if (notifications.existsByRecipientIdAndTypeAndTaskIdAndCreatedAtAfter(recipient, type, t.taskId(), startOfDay)) {
					continue;
				}
				notifications.save(Notification.forTask(recipient, type, null, t.taskId(), t.title(),
						t.dueDate().toString(), now));
				created++;
			}
		}
		log.info("Nhắc hạn việc: {} thông báo", created);
		return created;
	}

	/** Nhắc nộp báo cáo của ngày {@code today} (chỉ ngày làm việc). Trả về số thông báo đã tạo. */
	@Transactional
	public int remindReports(LocalDate today) {
		Instant startOfDay = startOfRealToday();
		Instant now = Instant.now(clock);
		int created = 0;
		for (MissingReports missing : reports.missingOn(today)) {
			for (Long userId : missing.userIds()) {
				if (notifications.existsByRecipientIdAndTypeAndCommunityIdAndCreatedAtAfter(userId,
						NotificationType.REPORT_REMINDER, missing.communityId(), startOfDay)) {
					continue;
				}
				notifications.save(Notification.forReport(userId, NotificationType.REPORT_REMINDER, null,
						missing.communityId(), null, null, missing.communityName(), null, now));
				created++;
			}
		}
		log.info("Nhắc nộp báo cáo: {} thông báo", created);
		return created;
	}

	/** Mốc chống gửi trùng: đầu ngày hôm nay thật (không phải ngày được truyền vào), nên chạy lại trong ngày không gửi lại. */
	private Instant startOfRealToday() {
		return today().atStartOfDay(zone).toInstant();
	}

	private Set<Long> activeAmong(Set<Long> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return users.findSummariesByIds(ids).stream().filter(UserSummary::active).map(UserSummary::id)
				.collect(Collectors.toSet());
	}
}

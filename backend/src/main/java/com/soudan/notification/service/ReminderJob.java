package com.soudan.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch chạy nhắc việc và nhắc báo cáo theo múi giờ nghiệp vụ ({@code app.timezone}). Tắt bằng {@code app.reminders.enabled=false}
 * (ví dụ khi test). Lỗi ở một lần chạy không làm dừng các lần sau.
 */
@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
class ReminderJob {

	private final ReminderService reminders;

	ReminderJob(ReminderService reminders) {
		this.reminders = reminders;
	}

	@Scheduled(cron = "${app.reminders.task-cron:0 0 8 * * *}", zone = "${app.timezone}")
	void remindTasks() {
		reminders.remindTasks(reminders.today());
	}

	@Scheduled(cron = "${app.reminders.report-cron:0 30 16 * * MON-FRI}", zone = "${app.timezone}")
	void remindReports() {
		reminders.remindReports(reminders.today());
	}
}

package com.soudan.report.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình báo cáo ({@code app.report.*}).
 *
 * @param workDays các ngày trong tuần phải nộp báo cáo; ngoài những ngày này không ai bị tính là "chưa nộp" và không nhắc
 */
@ConfigurationProperties("app.report")
public record ReportProperties(
		@DefaultValue({ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY" }) List<DayOfWeek> workDays) {

	public boolean isWorkDay(LocalDate date) {
		return workDays.contains(date.getDayOfWeek());
	}
}

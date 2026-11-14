package com.soudan.report.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một ngày nghỉ của một người (nghỉ phép). Ngày nghỉ không bị tính là chưa nộp báo cáo. */
@Entity
@Table(name = "report_day_off")
public class ReportDayOff {

	public static final int MAX_REASON = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private LocalDate day;

	@Column(length = MAX_REASON)
	private String reason;

	@Column(nullable = false)
	private Instant createdAt;

	protected ReportDayOff() {
		// JPA
	}

	public ReportDayOff(Long userId, LocalDate day, String reason, Instant createdAt) {
		this.userId = userId;
		this.day = day;
		this.createdAt = createdAt;
		changeReason(reason);
	}

	public void changeReason(String value) {
		String clean = value == null || value.isBlank() ? null : value.strip();
		this.reason = clean != null && clean.length() > MAX_REASON ? clean.substring(0, MAX_REASON) : clean;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDate getDay() {
		return day;
	}

	public String getReason() {
		return reason;
	}
}

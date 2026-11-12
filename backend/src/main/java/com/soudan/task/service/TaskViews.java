package com.soudan.task.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.soudan.task.domain.TaskPriority;
import com.soudan.task.domain.TaskStatus;

/** Các dạng dữ liệu service trả ra (đã ghép tên người và nhóm, dựng trong giao dịch). */
public final class TaskViews {

	private TaskViews() {
	}

	public record PersonView(Long id, String fullName) {
	}

	public record SubmissionView(Long id, String fileName, String contentType, long sizeBytes, Long uploadedBy,
			Instant uploadedAt) {
	}

	public record TaskView(Long id, Long teamId, String teamName, String title, String description, PersonView assignee,
			PersonView assignedBy, LocalDate dueDate, TaskPriority priority, TaskStatus status, boolean requireFile,
			String revisionReason, Long version, Instant createdAt, List<SubmissionView> submissions) {
	}

	public record HistoryView(Long id, TaskStatus fromStatus, TaskStatus toStatus, PersonView changedBy,
			Instant changedAt, String note) {
	}
}

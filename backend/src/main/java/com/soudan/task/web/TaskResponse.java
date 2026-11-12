package com.soudan.task.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.soudan.task.domain.TaskPriority;
import com.soudan.task.domain.TaskStatus;

public record TaskResponse(Long id, Long teamId, String teamName, String title, String description,
		PersonResponse assignee, PersonResponse assignedBy, LocalDate dueDate, TaskPriority priority,
		TaskStatus status, boolean requireFile, String revisionReason, Long version, Instant createdAt,
		List<SubmissionResponse> submissions) {

	public record PersonResponse(Long id, String fullName) {
	}

	public record SubmissionResponse(Long id, String fileName, String contentType, long sizeBytes, Long uploadedBy,
			Instant uploadedAt) {
	}
}

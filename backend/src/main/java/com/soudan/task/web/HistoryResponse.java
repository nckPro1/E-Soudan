package com.soudan.task.web;

import java.time.Instant;

import com.soudan.task.domain.TaskStatus;
import com.soudan.task.web.TaskResponse.PersonResponse;

public record HistoryResponse(Long id, TaskStatus fromStatus, TaskStatus toStatus, PersonResponse changedBy,
		Instant changedAt, String note) {
}

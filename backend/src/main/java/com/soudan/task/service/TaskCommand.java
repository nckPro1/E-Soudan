package com.soudan.task.service;

import java.time.LocalDate;

import com.soudan.task.domain.TaskPriority;

/** Dữ liệu tạo hoặc sửa task. */
public record TaskCommand(String title, String description, Long assigneeId, LocalDate dueDate, TaskPriority priority,
		boolean requireFile) {
}

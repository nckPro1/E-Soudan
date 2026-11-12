package com.soudan.task.web;

import java.time.LocalDate;

import com.soudan.task.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tạo task (không có version) và sửa task (bắt buộc version). Không có trường status: không sửa trạng thái trực tiếp. */
public record TaskRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 5000) String description,
		@NotNull Long assigneeId,
		LocalDate dueDate,
		TaskPriority priority,
		boolean requireFile,
		Long version) {
}

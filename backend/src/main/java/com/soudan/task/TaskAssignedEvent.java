package com.soudan.task;

import java.time.LocalDate;

/**
 * Phát ra khi một task được giao cho người phụ trách (tạo mới, hoặc đổi người phụ trách).
 * Module notification lắng nghe để gửi email; task không biết gì về email.
 */
public record TaskAssignedEvent(Long taskId, Long teamId, String title, Long assigneeId, Long assignedById,
		LocalDate dueDate) {
}

package com.soudan.task.domain;

/**
 * Luồng: ASSIGNED → IN_PROGRESS → PENDING_REVIEW → DONE. Trả lại để làm lại thì quay về IN_PROGRESS
 * (kèm lý do và một dòng lịch sử), không có trạng thái NEEDS_REVISION riêng.
 */
public enum TaskStatus {
	ASSIGNED,
	IN_PROGRESS,
	PENDING_REVIEW,
	DONE
}

package com.soudan.task;

/**
 * Phát ra khi Leader duyệt task ({@code approved} true) hoặc trả lại kèm lý do ({@code approved} false).
 * Người phụ trách cần được báo. Module notification lắng nghe; task không biết gì về thông báo.
 */
public record TaskReviewedEvent(Long taskId, Long teamId, String title, Long assigneeId, Long reviewerId,
		boolean approved, String reason) {
}

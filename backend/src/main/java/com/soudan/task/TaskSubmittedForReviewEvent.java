package com.soudan.task;

/** Phát ra khi người phụ trách đánh dấu task đã làm xong và chờ duyệt. Leader của nhóm cần được báo. */
public record TaskSubmittedForReviewEvent(Long taskId, Long teamId, String title, Long assigneeId) {
}

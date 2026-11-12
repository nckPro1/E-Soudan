package com.soudan.task;

/**
 * Phát ra khi có bình luận mới (kể cả trả lời). Module notification lắng nghe để gửi email.
 *
 * @param repliedToAuthorId tác giả của bình luận được trả lời; null nếu đây là bình luận gốc
 * @param assigneeId người phụ trách task
 */
public record TaskCommentedEvent(Long taskId, Long teamId, String title, Long commentId, Long authorId,
		Long assigneeId, Long repliedToAuthorId, String body) {
}

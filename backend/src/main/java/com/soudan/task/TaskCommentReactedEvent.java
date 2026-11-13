package com.soudan.task;

/** Phát ra khi có người thả biểu cảm mới vào một bình luận của task (không phát khi thả lại cùng emoji). */
public record TaskCommentReactedEvent(Long taskId, Long teamId, String title, Long commentId, Long commentAuthorId,
		Long actorId, String emoji) {
}

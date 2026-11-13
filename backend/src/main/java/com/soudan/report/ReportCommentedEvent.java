package com.soudan.report;

/**
 * Phát ra khi có bình luận mới (kể cả trả lời) trên một báo cáo.
 *
 * @param postAuthorId tác giả báo cáo (người có trang báo cáo đó)
 * @param repliedToAuthorId tác giả của bình luận được trả lời; null nếu là bình luận gốc
 */
public record ReportCommentedEvent(Long communityId, Long postId, String postTitle, Long postAuthorId,
		Long commentId, Long authorId, Long repliedToAuthorId, String body) {
}

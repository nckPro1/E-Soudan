package com.soudan.report;

/**
 * Phát ra khi có người thả biểu cảm mới vào một báo cáo hoặc một bình luận của báo cáo (không phát khi thả lại
 * cùng emoji).
 *
 * @param onComment true: biểu cảm trên một bình luận; false: trên chính báo cáo
 * @param targetAuthorId tác giả của thứ được thả biểu cảm (người cần được báo)
 */
public record ReportReactedEvent(Long communityId, Long postId, String postTitle, Long postAuthorId,
		boolean onComment, Long targetAuthorId, Long actorId, String emoji) {
}

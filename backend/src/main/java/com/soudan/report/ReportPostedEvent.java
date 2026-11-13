package com.soudan.report;

/** Phát ra khi một thành viên đăng báo cáo MỚI trong ngày (cập nhật báo cáo đã đăng thì không phát). Chủ cộng đồng cần được báo. */
public record ReportPostedEvent(Long communityId, Long postId, Long authorId, Long ownerId, String title) {
}

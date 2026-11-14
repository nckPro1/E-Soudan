package com.soudan.notification.domain;

/** Loại thông báo. Giao diện dựa vào loại để dựng câu chữ (theo ngôn ngữ người xem) và đường dẫn khi bấm vào. */
public enum NotificationType {
	/** Được giao một task */
	TASK_ASSIGNED,
	/** Một task đã nộp và đang chờ mình (Leader) duyệt */
	TASK_SUBMITTED,
	/** Task của mình đã được duyệt */
	TASK_APPROVED,
	/** Task của mình bị trả lại để làm lại */
	TASK_REVISION,
	/** Có bình luận mới trong một task liên quan tới mình */
	TASK_COMMENT,
	/** Có người trả lời bình luận của mình trong task */
	TASK_REPLY,
	/** Có người thả biểu cảm vào bình luận của mình trong task */
	TASK_REACTION,
	/** Một thành viên vừa đăng báo cáo mới (gửi cho chủ cộng đồng) */
	REPORT_POSTED,
	/** Có bình luận mới trên báo cáo của mình */
	REPORT_COMMENT,
	/** Có người trả lời bình luận của mình trên một báo cáo */
	REPORT_REPLY,
	/** Có người thả biểu cảm vào báo cáo của mình */
	REPORT_REACTION,
	/** Có người thả biểu cảm vào bình luận của mình trên một báo cáo */
	REPORT_COMMENT_REACTION,
	/** Có người muốn nhắn tin riêng với mình (cần mình chấp nhận) */
	CHAT_REQUEST,
	/** Lời mời nhắn tin của mình đã được chấp nhận */
	CHAT_REQUEST_ACCEPTED,
	/** Việc mình phụ trách đến hạn hôm nay hoặc ngày mai (hệ thống nhắc) */
	TASK_DUE_SOON,
	/** Việc đã quá hạn (gửi cho người phụ trách và Leader nhóm) */
	TASK_OVERDUE,
	/** Hôm nay mình chưa nộp báo cáo trong một cộng đồng (hệ thống nhắc) */
	REPORT_REMINDER
}

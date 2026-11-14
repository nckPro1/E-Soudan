export type NotificationType =
  | 'TASK_ASSIGNED'
  | 'TASK_SUBMITTED'
  | 'TASK_APPROVED'
  | 'TASK_REVISION'
  | 'TASK_COMMENT'
  | 'TASK_REPLY'
  | 'TASK_REACTION'
  | 'REPORT_POSTED'
  | 'REPORT_COMMENT'
  | 'REPORT_REPLY'
  | 'REPORT_REACTION'
  | 'REPORT_COMMENT_REACTION'
  | 'CHAT_REQUEST'
  | 'CHAT_REQUEST_ACCEPTED'
  | 'TASK_DUE_SOON'
  | 'TASK_OVERDUE'
  | 'REPORT_REMINDER'

export interface NotificationActor {
  id: number
  fullName: string
}

/** Một thông báo. Server lưu dữ liệu có cấu trúc; câu chữ do giao diện dựng theo ngôn ngữ người xem. */
export interface AppNotification {
  id: number
  type: NotificationType
  actor: NotificationActor | null
  taskId: number | null
  communityId: number | null
  postId: number | null
  /** Tác giả báo cáo: để mở đúng trang báo cáo riêng của người đó */
  postAuthorId: number | null
  /** Cuộc trò chuyện (lời mời nhắn tin) */
  conversationId: number | null
  /** Tên task hoặc tiêu đề báo cáo */
  subject: string | null
  /** Đoạn trích bình luận, lý do trả lại hoặc emoji */
  snippet: string | null
  createdAt: string
  read: boolean
}

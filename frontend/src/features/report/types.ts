export interface Person {
  id: number
  fullName: string
  position: string | null
}

export interface Reaction {
  emoji: string
  count: number
  reactedByMe: boolean
  /** Tên những người đã thả */
  users: string[]
}

export interface CommunitySummary {
  id: number
  name: string
  description: string | null
  owner: Person | null
  memberCount: number
  /** Mình là chủ (Manager tạo) cộng đồng này */
  owned: boolean
}

export interface CommunityDetail {
  community: CommunitySummary
  members: Person[]
  version: number
}

export interface ReportPost {
  id: number
  communityId: number
  author: Person | null
  /** yyyy-MM-dd theo múi giờ cấu hình ở server */
  reportDate: string
  title: string
  /** Thân bài rich-text (HTML đã lọc ở server; hiển thị qua sanitizeForDisplay); có thể rỗng nếu chỉ có file */
  body: string
  attachments: Attachment[]
  /** Mình là tác giả và hôm nay còn là ngày của báo cáo */
  editable: boolean
  edited: boolean
  createdAt: string
  version: number
  commentCount: number
  reactions: Reaction[]
}

export interface Attachment {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
  /** Là ảnh: hiện thumbnail thay vì chip file */
  image: boolean
}

export interface ReportInput {
  title: string
  body: string
  /** File và ảnh mới chọn */
  files: File[]
  /** File đã đính kèm lần trước mà người dùng gỡ đi */
  removeAttachmentIds: number[]
}

/** Bình luận. deleted = bình luận gốc đã xoá nhưng còn trả lời (khi đó author và body là null). */
export interface ReportComment {
  id: number
  parentId: number | null
  author: Person | null
  body: string | null
  deleted: boolean
  createdAt: string
  editedAt: string | null
  reactions: Reaction[]
}

export interface PendingReport {
  date: string
  /** false: không phải ngày làm việc (cuối tuần), không ai bị tính là chưa nộp */
  workingDay: boolean
  missing: Person[]
  /** Đã báo nghỉ ngày này nên không bị tính là chưa nộp */
  onLeave: Person[]
}

/** Một ngày nghỉ đã báo của chính mình. */
export interface DayOff {
  day: string
  reason: string | null
}

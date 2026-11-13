export type ConversationType = 'DIRECT' | 'TEAM'
export type DirectStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED'

export interface Person {
  id: number
  fullName: string
  position: string | null
}

export interface TeamRef {
  id: number
  name: string
  active: boolean
}

/** Tin cuối của một cuộc trò chuyện, để hiện ở danh sách. snippet null khi tin đã xoá hoặc chỉ có file. */
export interface LastMessage {
  id: number
  authorId: number
  authorName: string | null
  snippet: string | null
  deleted: boolean
  hasAttachment: boolean
  at: string
}

/**
 * Một cuộc trò chuyện theo góc nhìn của mình. DIRECT: có `other`. TEAM: có `team`.
 * `requestedByMe` chỉ có nghĩa khi DIRECT đang PENDING: mình gửi lời mời (chờ) hay mình là bên phải trả lời.
 */
export interface Conversation {
  id: number
  type: ConversationType
  status: DirectStatus
  requestedByMe: boolean
  canSend: boolean
  other: Person | null
  team: TeamRef | null
  last: LastMessage | null
  unread: number
  createdAt: string
}

export interface ChatAttachment {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
  image: boolean
}

export interface Reaction {
  emoji: string
  count: number
  reactedByMe: boolean
  /** Tên những người đã thả */
  users: string[]
}

/** Tin được trích dẫn khi trả lời. */
export interface ReplyRef {
  id: number
  author: Person | null
  snippet: string | null
  deleted: boolean
}

/** Một tin. deleted = đã xoá: body, attachments, reactions đều rỗng (lịch sử vẫn nằm ở server). */
export interface ChatMessage {
  id: number
  conversationId: number
  author: Person | null
  body: string | null
  deleted: boolean
  replyTo: ReplyRef | null
  attachments: ChatAttachment[]
  reactions: Reaction[]
  createdAt: string
  editedAt: string | null
}

export interface MessagePage {
  /** Mới nhất trước (đúng thứ tự server trả về) */
  items: ChatMessage[]
  hasMore: boolean
}

export interface UnreadEntry {
  conversationId: number
  teamId: number | null
  count: number
}

export interface UnreadSummary {
  total: number
  conversations: UnreadEntry[]
}

export interface SendInput {
  body: string
  replyToId?: number | null
  files: File[]
}

/** Sự kiện thời gian thực server đẩy xuống (WebSocket). */
export type ChatEventType = 'MESSAGE' | 'MESSAGE_UPDATED' | 'CONVERSATION' | 'CONVERSATION_REMOVED'

export interface ChatEvent {
  type: ChatEventType
  conversationId: number
  messageId: number | null
  /** Chỉ có ở MESSAGE: nội dung tin mới (dữ liệu chung, chưa có phần riêng của người xem) */
  message: ChatMessage | null
}

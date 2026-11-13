// Gọi API của feature chat. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { ChatMessage, Conversation, MessagePage, SendInput, UnreadSummary } from './types'

const BASE = '/chat'

export async function listConversations(): Promise<Conversation[]> {
  const { data } = await http.get<ApiSuccess<Conversation[]>>(`${BASE}/conversations`)
  return data.data
}

export async function getConversation(id: number): Promise<Conversation> {
  const { data } = await http.get<ApiSuccess<Conversation>>(`${BASE}/conversations/${id}`)
  return data.data
}

/** Cuộc nhắn riêng với một người (mọi trạng thái); null nếu chưa từng mời nhau. Dùng cho nút ở hồ sơ. */
export async function directWith(userId: number): Promise<Conversation | null> {
  const { data } = await http.get<ApiSuccess<Conversation | null>>(`${BASE}/direct/${userId}`)
  return data.data
}

/** Bấm "Nhắn tin" ở hồ sơ một người: gửi lời mời (hoặc mở cuộc trò chuyện nếu đã chấp nhận). */
export async function startDirect(userId: number): Promise<Conversation> {
  const { data } = await http.post<ApiSuccess<Conversation>>(`${BASE}/direct`, { userId })
  return data.data
}

export async function acceptRequest(id: number): Promise<Conversation> {
  const { data } = await http.post<ApiSuccess<Conversation>>(`${BASE}/conversations/${id}/accept`)
  return data.data
}

export async function declineRequest(id: number): Promise<Conversation> {
  const { data } = await http.post<ApiSuccess<Conversation>>(`${BASE}/conversations/${id}/decline`)
  return data.data
}

/** Người gửi thu hồi lời mời đang chờ. */
export async function cancelRequest(id: number): Promise<void> {
  await http.delete(`${BASE}/conversations/${id}`)
}

/** Phòng chat của một nhóm (server tạo ở lần đầu mở). */
export async function teamConversation(teamId: number): Promise<Conversation> {
  const { data } = await http.get<ApiSuccess<Conversation>>(`${BASE}/teams/${teamId}/conversation`)
  return data.data
}

export async function getUnread(): Promise<UnreadSummary> {
  const { data } = await http.get<ApiSuccess<UnreadSummary>>(`${BASE}/unread`)
  return data.data
}

export async function markRead(conversationId: number): Promise<void> {
  await http.post(`${BASE}/conversations/${conversationId}/read`)
}

/** Một trang lịch sử, mới nhất trước. `before` là id tin để lấy các tin cũ hơn. */
export async function history(conversationId: number, before: number | null, size: number): Promise<MessagePage> {
  const params: Record<string, number> = { size }
  if (before !== null) params.before = before
  const { data } = await http.get<ApiSuccess<MessagePage>>(`${BASE}/conversations/${conversationId}/messages`, { params })
  return data.data
}

export async function sendMessage(conversationId: number, input: SendInput): Promise<ChatMessage> {
  const form = new FormData()
  if (input.body) form.append('body', input.body)
  if (input.replyToId) form.append('replyToId', String(input.replyToId))
  input.files.forEach((file) => form.append('files', file))
  const { data } = await http.post<ApiSuccess<ChatMessage>>(`${BASE}/conversations/${conversationId}/messages`, form, {
    timeout: 120000,
  })
  return data.data
}

export async function getMessage(messageId: number): Promise<ChatMessage> {
  const { data } = await http.get<ApiSuccess<ChatMessage>>(`${BASE}/messages/${messageId}`)
  return data.data
}

export async function editMessage(messageId: number, body: string): Promise<ChatMessage> {
  const { data } = await http.put<ApiSuccess<ChatMessage>>(`${BASE}/messages/${messageId}`, { body })
  return data.data
}

export async function deleteMessage(messageId: number): Promise<void> {
  await http.delete(`${BASE}/messages/${messageId}`)
}

export async function reactToMessage(messageId: number, emoji: string): Promise<ChatMessage> {
  const { data } = await http.put<ApiSuccess<ChatMessage>>(`${BASE}/messages/${messageId}/reactions`, { emoji })
  return data.data
}

export async function removeMessageReaction(messageId: number, emoji: string): Promise<ChatMessage> {
  const { data } = await http.delete<ApiSuccess<ChatMessage>>(`${BASE}/messages/${messageId}/reactions`, {
    params: { emoji },
  })
  return data.data
}

/** Tải file đính kèm về dạng blob (cần gửi kèm token nên không dùng thẻ a/img trực tiếp). */
export async function downloadAttachment(messageId: number, attachmentId: number): Promise<Blob> {
  const { data } = await http.get<Blob>(`${BASE}/messages/${messageId}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
    timeout: 60000,
  })
  return data
}

// Gọi API của feature report (cộng đồng báo cáo). Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess, PageResult } from '../../shared/types/api'
import type {
  CommunityDetail,
  CommunitySummary,
  PendingReport,
  ReportComment,
  ReportInput,
  ReportPost,
} from './types'

export async function listCommunities(): Promise<CommunitySummary[]> {
  const { data } = await http.get<ApiSuccess<CommunitySummary[]>>('/communities')
  return data.data
}

export async function getCommunity(id: number): Promise<CommunityDetail> {
  const { data } = await http.get<ApiSuccess<CommunityDetail>>(`/communities/${id}`)
  return data.data
}

export async function createCommunity(name: string, description: string | null): Promise<CommunityDetail> {
  const { data } = await http.post<ApiSuccess<CommunityDetail>>('/communities', { name, description })
  return data.data
}

export async function updateCommunity(id: number, name: string, description: string | null): Promise<CommunityDetail> {
  const { data } = await http.put<ApiSuccess<CommunityDetail>>(`/communities/${id}`, { name, description })
  return data.data
}

export async function deleteCommunity(id: number): Promise<void> {
  await http.delete(`/communities/${id}`)
}

export async function addMembers(id: number, userIds: number[]): Promise<CommunityDetail> {
  const { data } = await http.post<ApiSuccess<CommunityDetail>>(`/communities/${id}/members`, { userIds })
  return data.data
}

export async function removeMember(id: number, userId: number): Promise<CommunityDetail> {
  const { data } = await http.delete<ApiSuccess<CommunityDetail>>(`/communities/${id}/members/${userId}`)
  return data.data
}

export async function getPending(id: number): Promise<PendingReport> {
  const { data } = await http.get<ApiSuccess<PendingReport>>(`/communities/${id}/pending`)
  return data.data
}

/** Báo cáo của MỘT thành viên (không có danh sách chung cả cộng đồng). */
export async function listPosts(id: number, authorId: number, page: number, size: number): Promise<PageResult<ReportPost>> {
  const { data } = await http.get<ApiSuccess<PageResult<ReportPost>>>(`/communities/${id}/posts`, {
    params: { authorId, page, size },
  })
  return data.data
}

export async function getMyToday(id: number): Promise<ReportPost | null> {
  const { data } = await http.get<ApiSuccess<ReportPost | null>>(`/communities/${id}/posts/mine-today`)
  return data.data
}

/** Nộp báo cáo hôm nay (multipart: tiêu đề, nội dung HTML, file mới, file cũ cần gỡ); đã nộp rồi thì server cập nhật. */
export async function submitReport(id: number, input: ReportInput): Promise<ReportPost> {
  const form = new FormData()
  form.append('title', input.title)
  form.append('body', input.body)
  input.files.forEach((file) => form.append('files', file))
  input.removeAttachmentIds.forEach((attachmentId) => form.append('removeAttachmentIds', String(attachmentId)))
  const { data } = await http.post<ApiSuccess<ReportPost>>(`/communities/${id}/posts`, form, { timeout: 120000 })
  return data.data
}

/** Tải file đính kèm về dạng blob (cần gửi kèm token nên không dùng thẻ a/img trực tiếp). */
export async function downloadAttachment(postId: number, attachmentId: number): Promise<Blob> {
  const { data } = await http.get<Blob>(`/report-posts/${postId}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
    timeout: 60000,
  })
  return data
}

export async function reactToPost(postId: number, emoji: string): Promise<ReportPost> {
  const { data } = await http.put<ApiSuccess<ReportPost>>(`/report-posts/${postId}/reactions`, { emoji })
  return data.data
}

export async function removePostReaction(postId: number, emoji: string): Promise<ReportPost> {
  const { data } = await http.delete<ApiSuccess<ReportPost>>(`/report-posts/${postId}/reactions`, { params: { emoji } })
  return data.data
}

export async function listComments(postId: number): Promise<ReportComment[]> {
  const { data } = await http.get<ApiSuccess<ReportComment[]>>(`/report-posts/${postId}/comments`)
  return data.data
}

export async function addComment(postId: number, body: string, replyToId?: number): Promise<ReportComment> {
  const { data } = await http.post<ApiSuccess<ReportComment>>(`/report-posts/${postId}/comments`, { body, replyToId })
  return data.data
}

export async function editComment(postId: number, commentId: number, body: string): Promise<ReportComment> {
  const { data } = await http.put<ApiSuccess<ReportComment>>(`/report-posts/${postId}/comments/${commentId}`, { body })
  return data.data
}

export async function deleteComment(postId: number, commentId: number): Promise<void> {
  await http.delete(`/report-posts/${postId}/comments/${commentId}`)
}

export async function reactToComment(postId: number, commentId: number, emoji: string): Promise<ReportComment> {
  const { data } = await http.put<ApiSuccess<ReportComment>>(
    `/report-posts/${postId}/comments/${commentId}/reactions`,
    { emoji },
  )
  return data.data
}

export async function removeCommentReaction(postId: number, commentId: number, emoji: string): Promise<ReportComment> {
  const { data } = await http.delete<ApiSuccess<ReportComment>>(
    `/report-posts/${postId}/comments/${commentId}/reactions`,
    { params: { emoji } },
  )
  return data.data
}

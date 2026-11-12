// Gọi API của feature task. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess, PageResult } from '../../shared/types/api'
import type { HistoryEntry, Submission, Task, TaskComment, TaskInput, TaskListQuery } from './types'

function params(query: TaskListQuery): Record<string, string | number> {
  const result: Record<string, string | number> = { page: query.page, size: query.size }
  if (query.q) result.q = query.q
  if (query.status) result.status = query.status
  if (query.assigneeId) result.assigneeId = query.assigneeId
  return result
}

export async function listTeamTasks(teamId: number, query: TaskListQuery): Promise<PageResult<Task>> {
  const { data } = await http.get<ApiSuccess<PageResult<Task>>>(`/teams/${teamId}/tasks`, { params: params(query) })
  return data.data
}

export async function listMyTasks(query: TaskListQuery): Promise<PageResult<Task>> {
  const { data } = await http.get<ApiSuccess<PageResult<Task>>>('/tasks/mine', { params: params(query) })
  return data.data
}

export async function getTask(id: number): Promise<Task> {
  const { data } = await http.get<ApiSuccess<Task>>(`/tasks/${id}`)
  return data.data
}

export async function createTask(teamId: number, input: TaskInput): Promise<Task> {
  const { data } = await http.post<ApiSuccess<Task>>(`/teams/${teamId}/tasks`, input)
  return data.data
}

export async function updateTask(id: number, input: TaskInput, version: number): Promise<Task> {
  const { data } = await http.put<ApiSuccess<Task>>(`/tasks/${id}`, { ...input, version })
  return data.data
}

type Action = 'start' | 'submit-for-review' | 'approve'

export async function transition(id: number, action: Action, version: number): Promise<Task> {
  const { data } = await http.post<ApiSuccess<Task>>(`/tasks/${id}/${action}`, { version })
  return data.data
}

export async function requestRevision(id: number, version: number, reason: string): Promise<Task> {
  const { data } = await http.post<ApiSuccess<Task>>(`/tasks/${id}/request-revision`, { version, reason })
  return data.data
}

export async function getHistory(id: number): Promise<HistoryEntry[]> {
  const { data } = await http.get<ApiSuccess<HistoryEntry[]>>(`/tasks/${id}/history`)
  return data.data
}

export async function uploadSubmission(id: number, file: File): Promise<Submission> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ApiSuccess<Submission>>(`/tasks/${id}/submissions`, form, { timeout: 60000 })
  return data.data
}

/** Tải file về dạng blob (cần gửi kèm token nên không dùng thẻ a trực tiếp). */
export async function downloadSubmission(taskId: number, submissionId: number): Promise<Blob> {
  const { data } = await http.get<Blob>(`/tasks/${taskId}/submissions/${submissionId}/download`, {
    responseType: 'blob',
    timeout: 60000,
  })
  return data
}

export async function listComments(taskId: number): Promise<TaskComment[]> {
  const { data } = await http.get<ApiSuccess<TaskComment[]>>(`/tasks/${taskId}/comments`)
  return data.data
}

export async function addComment(taskId: number, body: string, replyToId?: number): Promise<TaskComment> {
  const { data } = await http.post<ApiSuccess<TaskComment>>(`/tasks/${taskId}/comments`, { body, replyToId })
  return data.data
}

export async function editComment(taskId: number, commentId: number, body: string): Promise<TaskComment> {
  const { data } = await http.put<ApiSuccess<TaskComment>>(`/tasks/${taskId}/comments/${commentId}`, { body })
  return data.data
}

export async function deleteComment(taskId: number, commentId: number): Promise<void> {
  await http.delete(`/tasks/${taskId}/comments/${commentId}`)
}

export async function reactToComment(taskId: number, commentId: number, emoji: string): Promise<TaskComment> {
  const { data } = await http.put<ApiSuccess<TaskComment>>(`/tasks/${taskId}/comments/${commentId}/reactions`, { emoji })
  return data.data
}

export async function removeReaction(taskId: number, commentId: number, emoji: string): Promise<TaskComment> {
  const { data } = await http.delete<ApiSuccess<TaskComment>>(`/tasks/${taskId}/comments/${commentId}/reactions`, {
    params: { emoji },
  })
  return data.data
}

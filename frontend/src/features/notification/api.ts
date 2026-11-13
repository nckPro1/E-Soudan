// Gọi API của feature notification. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess, PageResult } from '../../shared/types/api'
import type { AppNotification } from './types'

export async function listNotifications(unreadOnly: boolean, page: number, size: number): Promise<PageResult<AppNotification>> {
  const { data } = await http.get<ApiSuccess<PageResult<AppNotification>>>('/notifications', {
    params: { unread: unreadOnly, page, size },
  })
  return data.data
}

export async function getUnreadCount(): Promise<number> {
  const { data } = await http.get<ApiSuccess<{ count: number }>>('/notifications/unread-count')
  return data.data.count
}

export async function markRead(id: number): Promise<AppNotification> {
  const { data } = await http.post<ApiSuccess<AppNotification>>(`/notifications/${id}/read`)
  return data.data
}

export async function markAllRead(): Promise<number> {
  const { data } = await http.post<ApiSuccess<{ updated: number }>>('/notifications/read-all')
  return data.data.updated
}

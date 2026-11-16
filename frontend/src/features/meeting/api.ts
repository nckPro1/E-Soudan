// Gọi API họp video. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { MeetingConfig, MeetingSession } from './types'

export async function getMeetingConfig(): Promise<MeetingConfig> {
  const { data } = await http.get<ApiSuccess<MeetingConfig>>('/public/meetings/config')
  return data.data
}

/** Người đăng nhập tạo phòng mới và trở thành chủ trì (token có quyền duyệt người vào phòng chờ). */
export async function createMeeting(displayName: string): Promise<MeetingSession> {
  const { data } = await http.post<ApiSuccess<MeetingSession>>('/meetings', { displayName })
  return data.data
}

/** Vào phòng bằng link (không cần tài khoản): token khách, phải chờ chủ trì cho vào. */
export async function joinMeeting(room: string, name: string): Promise<MeetingSession> {
  const { data } = await http.post<ApiSuccess<MeetingSession>>(`/public/meetings/${encodeURIComponent(room)}/join`, { name })
  return data.data
}

import type { MeetingSession } from './types'

/**
 * Chủ trì vừa tạo phòng giữ token trong trình duyệt để tải lại trang vẫn là chủ trì (backend không lưu cuộc họp).
 * Token hết hạn sau vài giờ nên chỉ giữ theo thời gian đó; mọi lỗi lưu trữ đều bỏ qua (trang vẫn chạy, chỉ mất việc nhớ).
 */
const PREFIX = 'soudan.meet.host.'
const KEEP_MS = 3 * 60 * 60 * 1000

interface Stored extends MeetingSession {
  savedAt: number
}

export function saveHostSession(session: MeetingSession): void {
  try {
    const value: Stored = { ...session, savedAt: Date.now() }
    localStorage.setItem(PREFIX + session.room, JSON.stringify(value))
  } catch {
    // trình duyệt chặn lưu trữ: bỏ qua
  }
}

export function loadHostSession(room: string): MeetingSession | null {
  try {
    const raw = localStorage.getItem(PREFIX + room)
    if (!raw) return null
    const value = JSON.parse(raw) as Stored
    if (Date.now() - value.savedAt > KEEP_MS) {
      localStorage.removeItem(PREFIX + room)
      return null
    }
    return { room: value.room, appId: value.appId, domain: value.domain, jwt: value.jwt }
  } catch {
    return null
  }
}

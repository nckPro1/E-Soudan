import { ref } from 'vue'
import { defineStore } from 'pinia'
import { isSoundEnabled, playChime, setSoundEnabled } from '../../shared/utils/sound'
import * as notificationApi from './api'

/** Khoảng cách giữa hai lần hỏi lại số thông báo chưa đọc. */
export const POLL_INTERVAL_MS = 30_000

/**
 * Số thông báo chưa đọc (hiện ở huy hiệu trên icon chuông). Cập nhật bằng cách hỏi lại server mỗi 30 giây, và ngay khi
 * người dùng quay lại tab. Chưa dùng WebSocket: đủ nhanh cho bản đầu và không cần kết nối giữ mở.
 * Khi số chưa đọc TĂNG so với lần hỏi trước (có thông báo mới từ người khác) thì phát tiếng "ting" nếu đang bật âm thanh.
 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  const soundOn = ref(isSoundEnabled())
  let timer: ReturnType<typeof setInterval> | undefined
  let started = false
  /** Số chưa đọc ở lần hỏi trước; null nghĩa là chưa hỏi lần nào (lần đầu chỉ ghi nhận, không kêu). */
  let known: number | null = null

  async function refresh() {
    try {
      const count = await notificationApi.getUnreadCount()
      if (known !== null && count > known && soundOn.value) playChime()
      known = count
      unread.value = count
    } catch {
      // Mất mạng hay hết phiên: giữ số cũ; lần hỏi sau sẽ thử lại (hết phiên thì cơ chế chung đưa về trang đăng nhập)
    }
  }

  function onVisible() {
    if (document.visibilityState === 'visible') void refresh()
  }

  function startPolling() {
    if (started) return
    started = true
    void refresh()
    timer = setInterval(() => void refresh(), POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisible)
  }

  function stopPolling() {
    started = false
    if (timer) clearInterval(timer)
    timer = undefined
    document.removeEventListener('visibilitychange', onVisible)
  }

  /** Đặt trực tiếp số chưa đọc (sau khi vừa đánh dấu đã đọc ở trang Hoạt động): thay đổi do chính mình, không kêu. */
  function set(count: number) {
    unread.value = Math.max(0, count)
    known = unread.value
  }

  /** Bật/tắt tiếng. Vừa bật thì kêu thử một tiếng để biết âm thanh hoạt động (và để trình duyệt cho phép phát). */
  function toggleSound() {
    soundOn.value = !soundOn.value
    setSoundEnabled(soundOn.value)
    if (soundOn.value) playChime()
  }

  function reset() {
    stopPolling()
    unread.value = 0
    known = null
  }

  return { unread, soundOn, refresh, startPolling, stopPolling, set, toggleSound, reset }
})

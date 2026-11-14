import type { OperatingHours } from './types'

export interface HoursStatus {
  /** Đang trong giờ hoạt động */
  open: boolean
  /** Số phút còn lại đến giờ đóng (chỉ có khi đang mở) */
  minutesLeft: number | null
}

const WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const toMinutes = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}

/**
 * Hệ thống đang mở hay đã đóng tại thời điểm {@code now}, tính theo múi giờ của lịch (không phải múi giờ của trình duyệt):
 * người xem ở Nhật vẫn thấy đúng giờ Việt Nam. Chỉ để hiển thị; việc bật/tắt máy thật do hạ tầng quyết định.
 */
export function statusAt(hours: OperatingHours, now: Date = new Date()): HoursStatus {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: hours.timezone,
    weekday: 'long',
    hour: 'numeric',
    minute: 'numeric',
    hourCycle: 'h23',
  }).formatToParts(now)
  const value = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  const day = value('weekday').toUpperCase()
  const minutes = Number(value('hour')) * 60 + Number(value('minute'))
  const start = toMinutes(hours.start)
  const end = toMinutes(hours.end)
  const open = hours.days.includes(day as OperatingHours['days'][number]) && minutes >= start && minutes < end
  return { open, minutesLeft: open ? end - minutes : null }
}

/**
 * Tên các ngày hoạt động theo ngôn ngữ: ba ngày liên tiếp trở lên gộp thành "Thứ hai – Thứ sáu", còn lại liệt kê.
 */
export function formatDays(days: OperatingHours['days'], locale: string): string {
  const order = WEEK.filter((d) => days.includes(d as OperatingHours['days'][number]))
  if (order.length === 0) return ''
  // 2026-01-05 là Thứ Hai
  const name = (d: string) => {
    const text = new Date(2026, 0, 5 + WEEK.indexOf(d)).toLocaleDateString(locale, { weekday: 'long' })
    return text.charAt(0).toUpperCase() + text.slice(1)
  }
  const first = WEEK.indexOf(order[0])
  const contiguous = order.every((d, i) => WEEK.indexOf(d) === first + i)
  if (contiguous && order.length >= 3) return `${name(order[0])} – ${name(order[order.length - 1])}`
  return order.map(name).join(', ')
}

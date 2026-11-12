const MINUTE = 60
const HOUR = 3600
const DAY = 86400

/**
 * Thời gian tương đối kiểu mạng xã hội: "5 phút trước", "2 giờ trước", "3 ngày trước"; quá 7 ngày thì hiện ngày.
 * Dưới một phút trả về null để chỗ gọi tự hiển thị "Vừa xong" theo ngôn ngữ của mình.
 */
export function timeAgo(iso: string, locale: string, now: number = Date.now()): string | null {
  const seconds = Math.round((now - new Date(iso).getTime()) / 1000)
  if (seconds < MINUTE) {
    return null
  }
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'always' })
  if (seconds < HOUR) {
    return format.format(-Math.floor(seconds / MINUTE), 'minute')
  }
  if (seconds < DAY) {
    return format.format(-Math.floor(seconds / HOUR), 'hour')
  }
  if (seconds < 7 * DAY) {
    return format.format(-Math.floor(seconds / DAY), 'day')
  }
  return new Date(iso).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' })
}

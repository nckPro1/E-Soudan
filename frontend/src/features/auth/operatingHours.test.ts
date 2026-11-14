import { describe, expect, it } from 'vitest'
import { formatDays, statusAt } from './operatingHours'
import type { OperatingHours } from './types'

const HOURS: OperatingHours = {
  start: '08:30',
  end: '18:00',
  days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  timezone: 'Asia/Ho_Chi_Minh',
}

// 2026-11-16 là Thứ Hai; Việt Nam = UTC+7, nên 10:00 giờ Việt Nam = 03:00 UTC
const at = (day: string, hhmmVn: string) => new Date(`${day}T${hhmmVn}:00+07:00`)

describe('statusAt', () => {
  it('should_beOpenWithMinutesLeft_duringHours', () => {
    expect(statusAt(HOURS, at('2026-11-16', '10:00'))).toEqual({ open: true, minutesLeft: 480 })
    expect(statusAt(HOURS, at('2026-11-16', '17:30'))).toEqual({ open: true, minutesLeft: 30 })
  })

  it('should_openExactlyAtStart_andCloseExactlyAtEnd', () => {
    expect(statusAt(HOURS, at('2026-11-16', '08:30')).open).toBe(true)
    expect(statusAt(HOURS, at('2026-11-16', '08:29')).open).toBe(false)
    expect(statusAt(HOURS, at('2026-11-16', '18:00')).open).toBe(false)
  })

  it('should_beClosedOnWeekend', () => {
    expect(statusAt(HOURS, at('2026-11-14', '10:00'))).toEqual({ open: false, minutesLeft: null }) // Thứ Bảy
    expect(statusAt(HOURS, at('2026-11-15', '10:00')).open).toBe(false) // Chủ Nhật
  })

  it('should_useTheScheduleTimezoneNotTheBrowserOne', () => {
    // 10:00 giờ Việt Nam; dù trình duyệt ở múi giờ nào, thời điểm tuyệt đối này vẫn là trong giờ
    expect(statusAt(HOURS, new Date('2026-11-16T03:00:00Z')).open).toBe(true)
    // 21:00 giờ Việt Nam (14:00 UTC) là ngoài giờ
    expect(statusAt(HOURS, new Date('2026-11-16T14:00:00Z')).open).toBe(false)
  })
})

describe('formatDays', () => {
  it('should_collapseConsecutiveDaysIntoRange', () => {
    expect(formatDays(HOURS.days, 'en')).toBe('Monday – Friday')
  })

  it('should_listNonConsecutiveDays', () => {
    expect(formatDays(['MONDAY', 'WEDNESDAY'], 'en')).toBe('Monday, Wednesday')
  })

  it('should_capitalizeVietnameseDayNames', () => {
    expect(formatDays(HOURS.days, 'vi')).toMatch(/^Thứ/)
  })

  it('should_returnEmpty_when_noDays', () => {
    expect(formatDays([], 'en')).toBe('')
  })
})

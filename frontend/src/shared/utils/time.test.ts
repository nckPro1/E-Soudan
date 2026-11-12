import { describe, expect, it } from 'vitest'
import { colorOf, initialOf } from './avatar'
import { timeAgo } from './time'

const NOW = new Date('2026-06-15T12:00:00Z').getTime()
const ago = (seconds: number) => new Date(NOW - seconds * 1000).toISOString()

describe('timeAgo', () => {
  it('should_returnNull_whenUnderAMinute', () => {
    expect(timeAgo(ago(0), 'en', NOW)).toBeNull()
    expect(timeAgo(ago(59), 'en', NOW)).toBeNull()
  })

  it('should_formatMinutesHoursAndDays', () => {
    expect(timeAgo(ago(60), 'en', NOW)).toBe('1 minute ago')
    expect(timeAgo(ago(5 * 60 + 30), 'en', NOW)).toBe('5 minutes ago')
    expect(timeAgo(ago(3 * 3600), 'en', NOW)).toBe('3 hours ago')
    expect(timeAgo(ago(2 * 86400), 'en', NOW)).toBe('2 days ago')
  })

  it('should_localizeToVietnamese', () => {
    expect(timeAgo(ago(5 * 60), 'vi', NOW)).toBe('5 phút trước')
    expect(timeAgo(ago(2 * 3600), 'vi', NOW)).toBe('2 giờ trước')
  })

  it('should_showADate_afterAWeek', () => {
    const text = timeAgo(ago(8 * 86400), 'en', NOW)

    expect(text).toContain('2026')
    expect(text).not.toContain('ago')
  })

  it('should_neverReturnNegativeOrFutureText_whenClockIsSlightlyBehind', () => {
    // Thời gian server nhanh hơn trình duyệt vài giây: coi như vừa xong
    expect(timeAgo(ago(-30), 'en', NOW)).toBeNull()
  })
})

describe('avatar helpers', () => {
  it('should_useFirstLetterOfLastWord', () => {
    expect(initialOf('Nguyễn Văn Lâm')).toBe('L')
    expect(initialOf('  minh  ')).toBe('M')
    expect(initialOf('田中 太郎')).toBe('太')
  })

  it('should_fallbackToQuestionMark_whenNameEmpty', () => {
    expect(initialOf('')).toBe('?')
    expect(initialOf('   ')).toBe('?')
  })

  it('should_giveTheSameColorForTheSameName_andAValidColor', () => {
    expect(colorOf('Lan')).toBe(colorOf('Lan'))
    expect(colorOf('Lan')).toMatch(/^#[0-9a-f]{6}$/)
  })
})

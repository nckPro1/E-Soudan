import { describe, expect, it } from 'vitest'
import { fromKey, monthGrid, shiftMonth, toKey } from './dates'

describe('calendar dates', () => {
  it('should_roundTripKey_when_localDate', () => {
    expect(toKey(fromKey('2026-11-13'))).toBe('2026-11-13')
    expect(toKey(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('should_startWeekOnMondayAndPadWholeWeeks_when_buildingGrid', () => {
    // 1/11/2026 là Chủ Nhật nên đứng cuối hàng đầu: 6 ô của tháng 10 đứng trước
    const grid = monthGrid(2026, 10)
    expect(grid.length % 7).toBe(0)
    expect(grid[6].key).toBe('2026-11-01')
    expect(grid[0].inMonth).toBe(false)
    expect(grid.filter((c) => c.inMonth)).toHaveLength(30)
  })

  it('should_wrapYear_when_shiftingMonth', () => {
    expect(shiftMonth(2026, 11, 1)).toEqual({ year: 2027, month: 0 })
    expect(shiftMonth(2026, 0, -1)).toEqual({ year: 2025, month: 11 })
  })
})

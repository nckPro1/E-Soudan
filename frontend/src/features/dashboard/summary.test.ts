import { describe, expect, it } from 'vitest'
import type { Task, TaskStatus } from '../task/types'
import { addDays, dayPart, digestTasks } from './summary'

const TODAY = '2026-11-14'

function task(id: number, dueDate: string | null, status: TaskStatus = 'IN_PROGRESS'): Task {
  return {
    id,
    teamId: 1,
    teamName: 'Alpha',
    title: `Việc ${id}`,
    description: null,
    assignee: { id: 9, fullName: 'Me' },
    assignedBy: null,
    dueDate,
    priority: 'MEDIUM',
    status,
    requireFile: false,
    revisionReason: null,
    version: 0,
    createdAt: '2026-11-01T00:00:00Z',
    submissions: [],
  }
}

describe('digestTasks', () => {
  it('should_splitOverdueDueSoonAndRest_andIgnoreDone', () => {
    const digest = digestTasks(
      [
        task(1, '2026-11-10'), // quá hạn
        task(2, '2026-11-14'), // hôm nay
        task(3, '2026-11-16'), // trong 2 ngày
        task(4, '2026-11-17'), // còn xa
        task(5, null),
        task(6, '2026-11-01', 'DONE'), // đã xong thì bỏ qua dù quá hạn
      ],
      TODAY,
    )

    expect(digest.open.map((t) => t.id)).toEqual([1, 2, 3, 4, 5])
    expect(digest.overdue.map((t) => t.id)).toEqual([1])
    expect(digest.dueSoon.map((t) => t.id)).toEqual([2, 3])
  })

  it('should_orderAttentionOverdueFirstThenSoonThenRest_andRespectLimit', () => {
    const digest = digestTasks([task(1, null), task(2, '2026-11-20'), task(3, '2026-11-15'), task(4, '2026-11-01')], TODAY, 3)

    expect(digest.attention.map((t) => t.id)).toEqual([4, 3, 2])
  })

  it('should_countWaitingReview', () => {
    const digest = digestTasks([task(1, null, 'PENDING_REVIEW'), task(2, null)], TODAY)

    expect(digest.waitingReview.map((t) => t.id)).toEqual([1])
  })

  it('should_returnEmpty_when_noTasks', () => {
    expect(digestTasks([], TODAY)).toEqual({ open: [], overdue: [], dueSoon: [], waitingReview: [], attention: [] })
  })
})

describe('helpers', () => {
  it('should_addDaysAcrossMonthBoundary', () => {
    expect(addDays('2026-11-29', 3)).toBe('2026-12-02')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
  })

  it('should_pickGreetingByHour', () => {
    expect(dayPart(new Date(2026, 10, 14, 8))).toBe('morning')
    expect(dayPart(new Date(2026, 10, 14, 13))).toBe('afternoon')
    expect(dayPart(new Date(2026, 10, 14, 20))).toBe('evening')
  })
})

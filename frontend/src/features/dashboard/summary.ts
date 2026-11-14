import { fromKey, toKey } from '../calendar/dates'
import type { Task } from '../task/types'

/** Việc đến hạn trong vòng ngần này ngày tính từ hôm nay (kể cả hôm nay) được coi là "sắp đến hạn". */
export const DUE_SOON_DAYS = 2

export interface TaskDigest {
  /** Việc chưa xong (chưa được duyệt) */
  open: Task[]
  overdue: Task[]
  dueSoon: Task[]
  /** Đã nộp, đang chờ Leader duyệt */
  waitingReview: Task[]
  /** Việc đáng chú ý nhất, theo thứ tự: quá hạn (trễ nhất trước), sắp đến hạn, rồi các việc còn lại theo hạn */
  attention: Task[]
}

export function addDays(key: string, days: number): string {
  const d = fromKey(key)
  d.setDate(d.getDate() + days)
  return toKey(d)
}

/** Phân loại các việc của mình để hiện trên trang tổng quan. {@code today} dạng YYYY-MM-DD. */
export function digestTasks(tasks: Task[], today: string, limit = 6): TaskDigest {
  const open = tasks.filter((t) => t.status !== 'DONE')
  const soonEnd = addDays(today, DUE_SOON_DAYS)
  const byDue = (a: Task, b: Task) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31') || a.id - b.id

  const overdue = open.filter((t) => t.dueDate !== null && t.dueDate < today).sort(byDue)
  const dueSoon = open.filter((t) => t.dueDate !== null && t.dueDate >= today && t.dueDate <= soonEnd).sort(byDue)
  const rest = open.filter((t) => !overdue.includes(t) && !dueSoon.includes(t)).sort(byDue)
  return {
    open,
    overdue,
    dueSoon,
    waitingReview: open.filter((t) => t.status === 'PENDING_REVIEW'),
    attention: [...overdue, ...dueSoon, ...rest].slice(0, limit),
  }
}

/** Lời chào theo giờ trong ngày. */
export function dayPart(date: Date): 'morning' | 'afternoon' | 'evening' {
  const hour = date.getHours()
  if (hour < 12) return 'morning'
  if (hour < 18) return 'afternoon'
  return 'evening'
}

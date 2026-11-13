/**
 * Tiện ích ngày cho lịch. Ngày lịch luôn là chuỗi YYYY-MM-DD theo giờ địa phương (không đi qua UTC) để ghi chú của
 * ngày 13 không bao giờ trôi sang ngày 12 hoặc 14 vì múi giờ.
 */

const pad = (n: number) => String(n).padStart(2, '0')

export function toKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function fromKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function todayKey(): string {
  return toKey(new Date())
}

export interface Cell {
  key: string
  day: number
  inMonth: boolean
}

/** Lưới của một tháng: các tuần đầy đủ, tuần bắt đầu từ Thứ Hai (như lịch ở Việt Nam và Nhật). 5 hoặc 6 hàng. */
export function monthGrid(year: number, month: number): Cell[] {
  const first = new Date(year, month, 1)
  const offset = (first.getDay() + 6) % 7 // Thứ Hai = 0
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const total = Math.ceil((offset + daysInMonth) / 7) * 7
  const cells: Cell[] = []
  for (let i = 0; i < total; i++) {
    const date = new Date(year, month, 1 - offset + i)
    cells.push({ key: toKey(date), day: date.getDate(), inMonth: date.getMonth() === month })
  }
  return cells
}

/** Thêm n tháng vào (năm, tháng) và chuẩn hoá. */
export function shiftMonth(year: number, month: number, delta: number): { year: number; month: number } {
  const d = new Date(year, month + delta, 1)
  return { year: d.getFullYear(), month: d.getMonth() }
}

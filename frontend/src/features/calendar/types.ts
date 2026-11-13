/** Màu đánh dấu cho phép (khớp CalendarNote.COLORS ở backend). */
export const NOTE_COLORS = ['INDIGO', 'RED', 'GREEN', 'ORANGE', 'BLUE', 'PURPLE'] as const
export type NoteColor = (typeof NOTE_COLORS)[number]

/** Mã màu hiển thị của từng màu đánh dấu (tông Teams). */
export const COLOR_HEX: Record<NoteColor, string> = {
  INDIGO: '#4f52b2',
  RED: '#c4314b',
  GREEN: '#107c10',
  ORANGE: '#ca5010',
  BLUE: '#0078d4',
  PURPLE: '#8764b8',
}

export const MAX_TITLE = 100
export const MAX_CONTENT = 2000

/** Một ghi chú trên lịch cá nhân. date dạng YYYY-MM-DD (ngày lịch, không có múi giờ). */
export interface CalendarNote {
  id: number
  date: string
  title: string
  content: string | null
  color: NoteColor
}

export interface NoteInput {
  date: string
  title: string
  content: string | null
  color: NoteColor
}

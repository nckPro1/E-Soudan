// Gọi API lịch cá nhân. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { CalendarNote, NoteInput } from './types'

const BASE = '/calendar/notes'

/** Ghi chú trong khoảng [from, to] (YYYY-MM-DD, gồm cả hai đầu). */
export async function listNotes(from: string, to: string): Promise<CalendarNote[]> {
  const { data } = await http.get<ApiSuccess<CalendarNote[]>>(BASE, { params: { from, to } })
  return data.data
}

export async function createNote(input: NoteInput): Promise<CalendarNote> {
  const { data } = await http.post<ApiSuccess<CalendarNote>>(BASE, input)
  return data.data
}

export async function updateNote(id: number, input: NoteInput): Promise<CalendarNote> {
  const { data } = await http.put<ApiSuccess<CalendarNote>>(`${BASE}/${id}`, input)
  return data.data
}

export async function deleteNote(id: number): Promise<void> {
  await http.delete(`${BASE}/${id}`)
}

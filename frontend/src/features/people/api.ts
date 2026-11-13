// Gọi API danh bạ và hồ sơ công khai. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { Person } from './types'

/** Tìm người trong công ty theo tên, email hoặc vị trí (không có Admin và tài khoản bị khoá). */
export async function searchPeople(q: string, size = 20): Promise<Person[]> {
  const { data } = await http.get<ApiSuccess<Person[]>>('/people', { params: { q, size } })
  return data.data
}

export async function getPerson(id: number): Promise<Person> {
  const { data } = await http.get<ApiSuccess<Person>>(`/people/${id}`)
  return data.data
}

// Gọi API của feature user. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { AssignableRole, CreateUserInput, ManagedUser, PageResult, UserListQuery } from './types'

export async function listUsers(query: UserListQuery): Promise<PageResult<ManagedUser>> {
  const params: Record<string, string | number> = { page: query.page, size: query.size }
  if (query.q) params.q = query.q
  if (query.status) params.status = query.status
  if (query.role) params.role = query.role
  const { data } = await http.get<ApiSuccess<PageResult<ManagedUser>>>('/users', { params })
  return data.data
}

export async function createUser(input: CreateUserInput): Promise<ManagedUser> {
  const { data } = await http.post<ApiSuccess<ManagedUser>>('/users', input)
  return data.data
}

export async function updateUser(
  id: number,
  input: { fullName: string; position: string | null; version: number },
): Promise<ManagedUser> {
  const { data } = await http.put<ApiSuccess<ManagedUser>>(`/users/${id}`, input)
  return data.data
}

export async function changeUserRole(id: number, systemRole: AssignableRole, version: number): Promise<ManagedUser> {
  const { data } = await http.put<ApiSuccess<ManagedUser>>(`/users/${id}/role`, { systemRole, version })
  return data.data
}

export async function lockUser(id: number): Promise<ManagedUser> {
  const { data } = await http.post<ApiSuccess<ManagedUser>>(`/users/${id}/lock`)
  return data.data
}

export async function unlockUser(id: number): Promise<ManagedUser> {
  const { data } = await http.post<ApiSuccess<ManagedUser>>(`/users/${id}/unlock`)
  return data.data
}

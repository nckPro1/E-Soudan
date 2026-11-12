import type { CurrentUser, SystemRole } from '../auth/types'

/** Tài khoản như backend trả ra (cùng hình dạng với hồ sơ của chính mình). */
export type ManagedUser = CurrentUser

export type UserStatus = 'ACTIVE' | 'LOCKED'

/** Vai trò gán được qua API (ADMIN chỉ có từ lúc khởi tạo hệ thống). */
export type AssignableRole = Exclude<SystemRole, 'ADMIN'>

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface UserListQuery {
  page: number
  size: number
  q?: string
  status?: UserStatus | ''
  role?: SystemRole | ''
}

export interface CreateUserInput {
  email: string
  temporaryPassword: string
  fullName: string
  position: string | null
  systemRole: AssignableRole
}

import type { CurrentUser, SystemRole } from '../auth/types'

/** Tài khoản như backend trả ra (cùng hình dạng với hồ sơ của chính mình). */
export type ManagedUser = CurrentUser

export type UserStatus = 'ACTIVE' | 'LOCKED'

/** Vai trò gán được qua API (ADMIN chỉ có từ lúc khởi tạo hệ thống). */
export type AssignableRole = Exclude<SystemRole, 'ADMIN'>

export type { PageResult } from '../../shared/types/api'

/** Người dùng rút gọn để chọn (thành viên nhóm...). */
export interface UserOption {
  id: number
  fullName: string
  position: string | null
  systemRole: SystemRole
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

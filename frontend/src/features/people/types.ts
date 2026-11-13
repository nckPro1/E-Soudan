import type { SystemRole } from '../auth/types'

/** Hồ sơ công khai trong công ty: chỉ thông tin hiển thị, không có trạng thái tài khoản. */
export interface Person {
  id: number
  fullName: string
  position: string | null
  email: string
  systemRole: SystemRole
}

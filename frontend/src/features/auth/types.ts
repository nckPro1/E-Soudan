export type SystemRole = 'ADMIN' | 'MANAGER' | 'USER'

/** Giờ hoạt động của hệ thống khi máy chủ chạy theo lịch (chỉ để hiển thị). */
export interface OperatingHours {
  start: string
  end: string
  days: ('MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY')[]
  timezone: string
}

export interface PublicInfo {
  /** null: máy chạy cả ngày, không có giờ hoạt động để hiển thị */
  operatingHours: OperatingHours | null
}

/** Tài khoản demo công khai cho người xem sản phẩm (chỉ có khi máy chủ bật demo). */
export interface DemoAccount {
  enabled: boolean
  email: string
  password: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  mustChangePassword: boolean
}

export interface CurrentUser {
  id: number
  email: string
  fullName: string
  position: string | null
  systemRole: SystemRole
  status: 'ACTIVE' | 'LOCKED'
  mustChangePassword: boolean
  version: number
  createdAt: string
}

export interface PermissionsResponse {
  systemRole: SystemRole
  permissions: string[]
}

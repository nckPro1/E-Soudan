export type SystemRole = 'ADMIN' | 'MANAGER' | 'USER'

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

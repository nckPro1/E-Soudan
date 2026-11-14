// Gọi API của feature auth. Component và store chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess } from '../../shared/types/api'
import type { CurrentUser, DemoAccount, PermissionsResponse, PublicInfo, TokenResponse } from './types'

/** Thông tin công khai cho trang đăng nhập (giờ hoạt động của hệ thống). */
export async function getPublicInfo(): Promise<PublicInfo> {
  const { data } = await http.get<ApiSuccess<PublicInfo>>('/public/info')
  return data.data
}

/** Thông tin tài khoản demo; {@code enabled=false} (và không có email/mật khẩu) khi máy chủ không bật demo. */
export async function getDemoAccount(): Promise<DemoAccount> {
  const { data } = await http.get<ApiSuccess<DemoAccount>>('/demo/account')
  return data.data
}

export async function login(email: string, password: string): Promise<TokenResponse> {
  const { data } = await http.post<ApiSuccess<TokenResponse>>('/auth/login', { email, password })
  return data.data
}

export async function refresh(refreshToken: string): Promise<TokenResponse> {
  const { data } = await http.post<ApiSuccess<TokenResponse>>('/auth/refresh', { refreshToken })
  return data.data
}

export async function logout(refreshToken: string): Promise<void> {
  await http.post('/auth/logout', { refreshToken })
}

export async function fetchMe(): Promise<CurrentUser> {
  const { data } = await http.get<ApiSuccess<CurrentUser>>('/me')
  return data.data
}

export async function fetchPermissions(): Promise<PermissionsResponse> {
  const { data } = await http.get<ApiSuccess<PermissionsResponse>>('/me/permissions')
  return data.data
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await http.post('/me/change-password', { currentPassword, newPassword })
}

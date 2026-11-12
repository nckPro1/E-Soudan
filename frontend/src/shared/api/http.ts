import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ApiError, type ApiErrorBody } from '../types/api'

declare module 'axios' {
  interface InternalAxiosRequestConfig {
    /** Đã thử làm mới token cho request này (tránh lặp vô hạn). */
    _retry?: boolean
  }
}

/**
 * Store đăng nhập đăng ký các hàm này lúc khởi tạo. Tách ra để http.ts không import store
 * (store gọi API qua http.ts, import hai chiều sẽ thành vòng phụ thuộc).
 */
export interface AuthHandlers {
  getAccessToken(): string | null
  /** Làm mới token; ném lỗi nếu không làm mới được. */
  refreshTokens(): Promise<void>
  onSessionExpired(): void
}

let handlers: AuthHandlers | null = null

export function registerAuthHandlers(value: AuthHandlers | null): void {
  handlers = value
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = handlers?.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Đường dẫn xác thực: lỗi 401 ở đây là "sai thông tin", không phải hết phiên, nên không làm mới token. */
function isAuthEndpoint(config: InternalAxiosRequestConfig): boolean {
  return (config.url ?? '').startsWith('/auth/')
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  if (error instanceof AxiosError) {
    const body = error.response?.data as Partial<ApiErrorBody> | undefined
    if (error.response && body && typeof body.code === 'string') {
      return new ApiError(body.code, error.response.status, body.message ?? '', body.details ?? [])
    }
    if (!error.response) {
      return new ApiError('NETWORK_ERROR', 0, error.message)
    }
    return new ApiError('UNKNOWN', error.response.status, error.message)
  }
  return new ApiError('UNKNOWN', 0, error instanceof Error ? error.message : 'Unknown error')
}

http.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const apiError = toApiError(error)
    const config = error instanceof AxiosError ? error.config : undefined

    const canRefresh =
      handlers !== null &&
      config !== undefined &&
      apiError.status === 401 &&
      !config._retry &&
      !isAuthEndpoint(config)

    if (canRefresh) {
      config._retry = true
      try {
        await handlers!.refreshTokens()
      } catch (refreshError) {
        // Refresh token hết hạn, bị thu hồi hoặc tài khoản bị khoá => hết phiên. Lỗi mạng thì giữ nguyên phiên.
        const failure = toApiError(refreshError)
        if (failure.status === 401 || failure.status === 403) {
          handlers!.onSessionExpired()
        }
        throw failure
      }
      // Request thử lại; lỗi của nó (ví dụ 403 không đủ quyền) trả về bình thường, không đăng xuất.
      return http(config)
    }
    throw apiError
  },
)

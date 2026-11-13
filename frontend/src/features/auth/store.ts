import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { registerAuthHandlers } from '../../shared/api/http'
import * as authApi from './api'
import type { CurrentUser, TokenResponse } from './types'

const REFRESH_KEY = 'soudan.refreshToken'
const LAST_EMAIL_KEY = 'soudan.lastEmail'

function readStorage(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(storage: Storage, key: string, value: string | null): void {
  try {
    if (value === null) {
      storage.removeItem(key)
    } else {
      storage.setItem(key, value)
    }
  } catch {
    // Không ghi được (chế độ riêng tư...): phiên chỉ sống đến khi tải lại trang.
  }
}

/**
 * Trạng thái đăng nhập. Access token chỉ giữ trong bộ nhớ (mất khi tải lại trang rồi lấy lại bằng refresh token).
 * Refresh token lưu localStorage vì backend trả qua JSON; đây là đánh đổi chấp nhận được cho MVP (xem SETUP.md).
 */
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(readStorage(localStorage, REFRESH_KEY))
  const user = ref<CurrentUser | null>(null)
  const permissions = ref<string[]>([])
  const initialized = ref(false)
  /** Email đăng nhập gần nhất, để điền sẵn khi phiên hết hạn (như màn "Session Expired"). */
  const lastEmail = ref<string>(readStorage(sessionStorage, LAST_EMAIL_KEY) ?? '')

  let refreshInFlight: Promise<void> | null = null
  let sessionExpiredHandler: () => void = () => {}

  const isAuthenticated = computed(() => accessToken.value !== null && user.value !== null)
  const mustChangePassword = computed(() => user.value?.mustChangePassword ?? false)

  function setTokens(tokens: TokenResponse): void {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    writeStorage(localStorage, REFRESH_KEY, tokens.refreshToken)
  }

  function clear(): void {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    permissions.value = []
    writeStorage(localStorage, REFRESH_KEY, null)
  }

  /** Nạp hồ sơ và quyền. Khi đang bị buộc đổi mật khẩu, backend chặn /me/permissions nên bỏ qua. */
  async function loadProfile(): Promise<void> {
    const me = await authApi.fetchMe()
    user.value = me
    permissions.value = me.mustChangePassword ? [] : (await authApi.fetchPermissions()).permissions
  }

  async function login(email: string, password: string): Promise<void> {
    const tokens = await authApi.login(email, password)
    setTokens(tokens)
    // Vừa đăng nhập tức là đã có phiên sống: không cần "khôi phục phiên" từ refresh token nữa.
    initialized.value = true
    lastEmail.value = email
    writeStorage(sessionStorage, LAST_EMAIL_KEY, email)
    await loadProfile()
  }

  /** Làm mới token (xoay vòng). Nhiều request cùng hết hạn một lúc chỉ gọi refresh một lần. */
  function refreshTokens(): Promise<void> {
    if (!refreshToken.value) {
      return Promise.reject(new Error('No refresh token'))
    }
    refreshInFlight ??= authApi
      .refresh(refreshToken.value)
      .then(setTokens)
      .catch((error: unknown) => {
        clear()
        throw error
      })
      .finally(() => {
        refreshInFlight = null
      })
    return refreshInFlight
  }

  /** Khôi phục phiên sau khi tải lại trang. Gọi một lần ở router guard. */
  async function restore(): Promise<void> {
    if (initialized.value) {
      return
    }
    try {
      if (refreshToken.value) {
        await refreshTokens()
        await loadProfile()
      }
    } catch {
      clear()
    } finally {
      initialized.value = true
    }
  }

  async function logout(): Promise<void> {
    const token = refreshToken.value
    clear()
    if (token) {
      try {
        await authApi.logout(token)
      } catch {
        // Thu hồi phía server thất bại cũng không sao: token cục bộ đã bị xoá.
      }
    }
  }

  async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await authApi.changePassword(currentPassword, newPassword)
    await loadProfile()
  }

  function setSessionExpiredHandler(handler: () => void): void {
    sessionExpiredHandler = handler
  }

  registerAuthHandlers({
    getAccessToken: () => accessToken.value,
    refreshTokens,
    onSessionExpired: () => {
      clear()
      sessionExpiredHandler()
    },
  })

  return {
    /** Access token hiện tại (chỉ giữ trong bộ nhớ); dùng cho kết nối WebSocket, các lệnh REST đã được axios tự gắn. */
    accessToken,
    user,
    permissions,
    initialized,
    lastEmail,
    isAuthenticated,
    mustChangePassword,
    login,
    logout,
    refreshTokens,
    restore,
    changePassword,
    setSessionExpiredHandler,
  }
})

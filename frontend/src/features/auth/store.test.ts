import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../../shared/types/api'
import * as api from './api'
import { useAuthStore } from './store'
import type { CurrentUser, TokenResponse } from './types'

vi.mock('./api')

const tokens = (suffix = '1', mustChangePassword = false): TokenResponse => ({
  accessToken: `access-${suffix}`,
  refreshToken: `refresh-${suffix}`,
  tokenType: 'Bearer',
  expiresIn: 900,
  mustChangePassword,
})

const user = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  id: 1,
  email: 'admin@soudan.local',
  fullName: 'Admin',
  position: null,
  systemRole: 'ADMIN',
  status: 'ACTIVE',
  mustChangePassword: false,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
  })

  it('should_storeSessionAndLoadProfile_when_loginSucceeds', async () => {
    vi.mocked(api.login).mockResolvedValue(tokens())
    vi.mocked(api.fetchMe).mockResolvedValue(user())
    vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'ADMIN', permissions: ['USER_READ'] })
    const auth = useAuthStore()

    await auth.login('admin@soudan.local', 'secret')

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user?.email).toBe('admin@soudan.local')
    expect(auth.permissions).toEqual(['USER_READ'])
    expect(localStorage.getItem('soudan.refreshToken')).toBe('refresh-1')
    expect(auth.lastEmail).toBe('admin@soudan.local')
  })

  it('should_skipPermissions_when_passwordChangeIsRequired', async () => {
    vi.mocked(api.login).mockResolvedValue(tokens('1', true))
    vi.mocked(api.fetchMe).mockResolvedValue(user({ mustChangePassword: true }))
    const auth = useAuthStore()

    await auth.login('admin@soudan.local', 'temp')

    expect(auth.mustChangePassword).toBe(true)
    expect(api.fetchPermissions).not.toHaveBeenCalled()
    expect(auth.permissions).toEqual([])
  })

  it('should_stayLoggedOut_when_loginFails', async () => {
    vi.mocked(api.login).mockRejectedValue(new ApiError('INVALID_CREDENTIALS', 401, 'x'))
    const auth = useAuthStore()

    await expect(auth.login('a@b.c', 'wrong')).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })

    expect(auth.isAuthenticated).toBe(false)
    expect(localStorage.getItem('soudan.refreshToken')).toBeNull()
  })

  it('should_restoreSession_when_refreshTokenStored', async () => {
    localStorage.setItem('soudan.refreshToken', 'refresh-old')
    vi.mocked(api.refresh).mockResolvedValue(tokens('2'))
    vi.mocked(api.fetchMe).mockResolvedValue(user())
    vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'ADMIN', permissions: [] })
    const auth = useAuthStore()

    await auth.restore()

    expect(api.refresh).toHaveBeenCalledWith('refresh-old')
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.initialized).toBe(true)
    // token xoay vòng: bản mới thay bản cũ
    expect(localStorage.getItem('soudan.refreshToken')).toBe('refresh-2')
  })

  it('should_clearEverything_when_restoreFails', async () => {
    localStorage.setItem('soudan.refreshToken', 'refresh-revoked')
    vi.mocked(api.refresh).mockRejectedValue(new ApiError('UNAUTHORIZED', 401, 'x'))
    const auth = useAuthStore()

    await auth.restore()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.initialized).toBe(true)
    expect(localStorage.getItem('soudan.refreshToken')).toBeNull()
  })

  it('should_notCallApi_when_restoreWithoutToken', async () => {
    const auth = useAuthStore()

    await auth.restore()

    expect(api.refresh).not.toHaveBeenCalled()
    expect(auth.initialized).toBe(true)
  })

  it('should_restoreOnlyOnce_when_calledTwice', async () => {
    const auth = useAuthStore()

    await auth.restore()
    localStorage.setItem('soudan.refreshToken', 'late')
    await auth.restore()

    expect(api.refresh).not.toHaveBeenCalled()
  })

  it('should_callRefreshOnce_when_manyRequestsRefreshAtTheSameTime', async () => {
    localStorage.setItem('soudan.refreshToken', 'refresh-old')
    let resolve!: (value: TokenResponse) => void
    vi.mocked(api.refresh).mockReturnValue(new Promise<TokenResponse>((r) => (resolve = r)))
    const auth = useAuthStore()

    const first = auth.refreshTokens()
    const second = auth.refreshTokens()
    const third = auth.refreshTokens()
    resolve(tokens('9'))
    await Promise.all([first, second, third])

    expect(api.refresh).toHaveBeenCalledTimes(1)
  })

  it('should_allowANewRefresh_when_previousOneFinished', async () => {
    localStorage.setItem('soudan.refreshToken', 'refresh-old')
    vi.mocked(api.refresh).mockResolvedValueOnce(tokens('2')).mockResolvedValueOnce(tokens('3'))
    const auth = useAuthStore()

    await auth.refreshTokens()
    await auth.refreshTokens()

    expect(api.refresh).toHaveBeenCalledTimes(2)
    expect(api.refresh).toHaveBeenLastCalledWith('refresh-2')
  })

  it('should_clearSession_when_refreshFails', async () => {
    localStorage.setItem('soudan.refreshToken', 'refresh-old')
    vi.mocked(api.refresh).mockRejectedValue(new ApiError('UNAUTHORIZED', 401, 'x'))
    const auth = useAuthStore()

    await expect(auth.refreshTokens()).rejects.toBeInstanceOf(ApiError)

    expect(localStorage.getItem('soudan.refreshToken')).toBeNull()
  })

  it('should_revokeOnServerAndClearLocal_when_logout', async () => {
    vi.mocked(api.login).mockResolvedValue(tokens())
    vi.mocked(api.fetchMe).mockResolvedValue(user())
    vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'ADMIN', permissions: ['USER_READ'] })
    vi.mocked(api.logout).mockResolvedValue()
    const auth = useAuthStore()
    await auth.login('admin@soudan.local', 'secret')

    await auth.logout()

    expect(api.logout).toHaveBeenCalledWith('refresh-1')
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.permissions).toEqual([])
    expect(localStorage.getItem('soudan.refreshToken')).toBeNull()
  })

  it('should_stillClearLocal_when_serverLogoutFails', async () => {
    localStorage.setItem('soudan.refreshToken', 'refresh-old')
    vi.mocked(api.logout).mockRejectedValue(new ApiError('NETWORK_ERROR', 0, 'offline'))
    const auth = useAuthStore()

    await auth.logout()

    expect(localStorage.getItem('soudan.refreshToken')).toBeNull()
  })

  it('should_reloadProfileWithPermissions_when_passwordChanged', async () => {
    vi.mocked(api.login).mockResolvedValue(tokens('1', true))
    vi.mocked(api.fetchMe)
      .mockResolvedValueOnce(user({ mustChangePassword: true }))
      .mockResolvedValueOnce(user({ mustChangePassword: false }))
    vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'ADMIN', permissions: ['USER_READ'] })
    vi.mocked(api.changePassword).mockResolvedValue()
    const auth = useAuthStore()
    await auth.login('admin@soudan.local', 'temp')

    await auth.changePassword('temp', 'brand-new-pass')

    expect(auth.mustChangePassword).toBe(false)
    expect(auth.permissions).toEqual(['USER_READ'])
  })
})

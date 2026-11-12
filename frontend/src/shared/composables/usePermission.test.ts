import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as api from '../../features/auth/api'
import { useAuthStore } from '../../features/auth/store'
import type { SystemRole } from '../../features/auth/types'
import { usePermission } from './usePermission'

vi.mock('../../features/auth/api')

async function signInAs(role: SystemRole, permissions: string[]) {
  vi.mocked(api.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: false,
  })
  vi.mocked(api.fetchMe).mockResolvedValue({
    id: 1,
    email: 'x@soudan.local',
    fullName: 'X',
    position: null,
    systemRole: role,
    status: 'ACTIVE',
    mustChangePassword: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: role, permissions })
  await useAuthStore().login('x@soudan.local', 'pw')
}

describe('usePermission', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
  })

  it('should_denyEverything_when_notSignedIn', () => {
    const { can, isAdmin, isManager, hasRole, role } = usePermission()

    expect(can('USER_READ')).toBe(false)
    expect(isAdmin.value).toBe(false)
    expect(isManager.value).toBe(false)
    expect(hasRole('ADMIN', 'MANAGER', 'USER')).toBe(false)
    expect(role.value).toBeNull()
  })

  it('should_grantAdminPermissions_when_signedInAsAdmin', async () => {
    await signInAs('ADMIN', ['USER_READ', 'USER_CREATE'])
    const { can, isAdmin, hasRole } = usePermission()

    expect(can('USER_CREATE')).toBe(true)
    expect(can('USER_LOCK')).toBe(false)
    expect(isAdmin.value).toBe(true)
    expect(hasRole('ADMIN')).toBe(true)
    expect(hasRole('USER')).toBe(false)
  })

  it('should_haveNoPermissions_when_signedInAsUser', async () => {
    await signInAs('USER', [])
    const { can, isAdmin, isManager, hasRole } = usePermission()

    expect(can('USER_READ')).toBe(false)
    expect(isAdmin.value).toBe(false)
    expect(isManager.value).toBe(false)
    expect(hasRole('USER')).toBe(true)
  })

  it('should_recognizeManager_when_signedInAsManager', async () => {
    await signInAs('MANAGER', [])
    const { isManager, hasRole } = usePermission()

    expect(isManager.value).toBe(true)
    expect(hasRole('MANAGER', 'ADMIN')).toBe(true)
  })
})

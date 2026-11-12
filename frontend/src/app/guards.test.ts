import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as api from '../features/auth/api'
import { useAuthStore } from '../features/auth/store'
import type { CurrentUser } from '../features/auth/types'
import { installGuards } from './guards'

vi.mock('../features/auth/api')

const blank = { render: () => null }

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: blank },
      { path: '/login', name: 'login', component: blank, meta: { guestOnly: true } },
      { path: '/change-password', name: 'change-password', component: blank },
      { path: '/users', name: 'users', component: blank, meta: { permission: 'USER_READ' } },
    ],
  })
  installGuards(router)
  return router
}

const user = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  id: 1,
  email: 'a@soudan.local',
  fullName: 'A',
  position: null,
  systemRole: 'ADMIN',
  status: 'ACTIVE',
  mustChangePassword: false,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

async function signIn(me: CurrentUser, permissions: string[]) {
  vi.mocked(api.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: me.mustChangePassword,
  })
  vi.mocked(api.fetchMe).mockResolvedValue(me)
  vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: me.systemRole, permissions })
  await useAuthStore().login(me.email, 'pw')
}

describe('router guards', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
  })

  it('should_redirectToLoginWithTarget_when_notAuthenticated', async () => {
    const router = makeRouter()

    await router.push('/users')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/users')
  })

  it('should_redirectToLoginWithoutTarget_when_notAuthenticatedAtRoot', async () => {
    const router = makeRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBeUndefined()
  })

  it('should_openLogin_when_notAuthenticated', async () => {
    const router = makeRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('should_sendLoggedInUserHome_when_visitingLogin', async () => {
    await signIn(user(), ['USER_READ'])
    const router = makeRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('should_forceChangePassword_when_flagged', async () => {
    await signIn(user({ mustChangePassword: true }), [])
    const router = makeRouter()

    await router.push('/')
    expect(router.currentRoute.value.name).toBe('change-password')

    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('change-password')
  })

  it('should_allowChangePasswordPage_when_flagged', async () => {
    await signIn(user({ mustChangePassword: true }), [])
    const router = makeRouter()

    await router.push('/change-password')

    expect(router.currentRoute.value.name).toBe('change-password')
  })

  it('should_sendHome_when_permissionMissing', async () => {
    await signIn(user({ systemRole: 'USER' }), [])
    const router = makeRouter()

    await router.push('/users')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('should_allowRoute_when_permissionPresent', async () => {
    await signIn(user(), ['USER_READ'])
    const router = makeRouter()

    await router.push('/users')

    expect(router.currentRoute.value.name).toBe('users')
  })

  it('should_restoreSessionBeforeDeciding_when_refreshTokenStored', async () => {
    localStorage.setItem('soudan.refreshToken', 'stored')
    vi.mocked(api.refresh).mockResolvedValue({
      accessToken: 'a2',
      refreshToken: 'r2',
      tokenType: 'Bearer',
      expiresIn: 900,
      mustChangePassword: false,
    })
    vi.mocked(api.fetchMe).mockResolvedValue(user())
    vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'ADMIN', permissions: ['USER_READ'] })
    const router = makeRouter()

    await router.push('/users')

    expect(router.currentRoute.value.name).toBe('users')
  })
})

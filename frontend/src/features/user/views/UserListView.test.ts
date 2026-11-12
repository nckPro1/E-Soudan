import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import en from '../../../shared/locales/en.json'
import * as authApi from '../../auth/api'
import { useAuthStore } from '../../auth/store'
import * as userApi from '../api'
import type { ManagedUser } from '../types'
import UserListView from './UserListView.vue'

vi.mock('../../auth/api')
vi.mock('../api')

const make = (overrides: Partial<ManagedUser>): ManagedUser => ({
  id: 2,
  email: 'b@soudan.local',
  fullName: 'Bao',
  position: 'Dev',
  systemRole: 'USER',
  status: 'ACTIVE',
  mustChangePassword: false,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

const page = (items: ManagedUser[]) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1 })

async function mountView(permissions: string[]) {
  const me = make({ id: 1, email: 'a@soudan.local', fullName: 'Admin', systemRole: 'ADMIN' })
  vi.mocked(authApi.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: false,
  })
  vi.mocked(authApi.fetchMe).mockResolvedValue(me)
  vi.mocked(authApi.fetchPermissions).mockResolvedValue({ systemRole: 'ADMIN', permissions })
  await useAuthStore().login(me.email, 'pw')

  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const wrapper = mount(UserListView, { global: { plugins: [i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

describe('UserListView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('should_listUsers_when_opened', async () => {
    vi.mocked(userApi.listUsers).mockResolvedValue(page([make({ fullName: 'Bao' })]))

    const wrapper = await mountView(['USER_READ'])

    expect(wrapper.text()).toContain('Bao')
    expect(wrapper.text()).toContain('b@soudan.local')
  })

  it('should_showEmptyMessage_when_noUsers', async () => {
    vi.mocked(userApi.listUsers).mockResolvedValue(page([]))

    const wrapper = await mountView(['USER_READ'])

    expect(wrapper.text()).toContain('No accounts found.')
  })

  it('should_hideManageButtons_when_onlyReadPermission', async () => {
    vi.mocked(userApi.listUsers).mockResolvedValue(page([make({})]))

    const wrapper = await mountView(['USER_READ'])

    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons).toEqual([])
  })

  it('should_notOfferLock_when_rowIsCurrentUser', async () => {
    vi.mocked(userApi.listUsers).mockResolvedValue(
      page([make({ id: 1, fullName: 'Admin', systemRole: 'ADMIN' })]),
    )

    const wrapper = await mountView(['USER_READ', 'USER_UPDATE', 'USER_LOCK'])

    expect(wrapper.find('button.link.danger').exists()).toBe(false)
  })

  it('should_lockUser_when_confirmed', async () => {
    vi.mocked(userApi.listUsers).mockResolvedValue(page([make({})]))
    vi.mocked(userApi.lockUser).mockResolvedValue(make({ status: 'LOCKED' }))

    const wrapper = await mountView(['USER_READ', 'USER_LOCK'])
    await wrapper.find('button.link.danger').trigger('click')
    const confirm = Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Confirm')
    confirm?.click()
    await flushPromises()

    expect(userApi.lockUser).toHaveBeenCalledWith(2)
    expect(userApi.listUsers).toHaveBeenCalledTimes(2)
  })

  it('should_showErrorMessage_when_listFails', async () => {
    vi.mocked(userApi.listUsers).mockRejectedValue(new Error('boom'))

    const wrapper = await mountView(['USER_READ'])

    expect(wrapper.text()).toContain('Something went wrong')
  })
})

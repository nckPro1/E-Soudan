import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import en from '../../../shared/locales/en.json'
import * as authApi from '../../auth/api'
import { useAuthStore } from '../../auth/store'
import type { SystemRole } from '../../auth/types'
import * as teamApi from '../api'
import type { TeamDetail } from '../types'
import TeamDetailView from './TeamDetailView.vue'

vi.mock('../../auth/api')
vi.mock('../api')
vi.mock('../../user/api', () => ({ listUserOptions: vi.fn().mockResolvedValue([]) }))

const team = (overrides: Partial<TeamDetail> = {}): TeamDetail => ({
  id: 7,
  name: 'Alpha',
  description: 'First team',
  status: 'ACTIVE',
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  members: [
    { userId: 1, fullName: 'Lan', position: 'Dev', teamRole: 'LEADER', joinedAt: '2026-01-01T00:00:00Z' },
    { userId: 2, fullName: 'Minh', position: null, teamRole: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z' },
    { userId: 3, fullName: 'Quan', position: null, teamRole: 'OBSERVER', joinedAt: '2026-01-01T00:00:00Z' },
  ],
  ...overrides,
})

async function mountView(role: SystemRole, permissions: string[], detail: TeamDetail = team()) {
  vi.mocked(authApi.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: false,
  })
  vi.mocked(authApi.fetchMe).mockResolvedValue({
    id: 9,
    email: 'x@soudan.local',
    fullName: 'X',
    position: null,
    systemRole: role,
    status: 'ACTIVE',
    mustChangePassword: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(authApi.fetchPermissions).mockResolvedValue({ systemRole: role, permissions })
  vi.mocked(teamApi.getTeam).mockResolvedValue(detail)
  await useAuthStore().login('x@soudan.local', 'pw')

  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/teams', name: 'teams', component: { render: () => null } }] })
  const wrapper = mount(TeamDetailView, { props: { id: 7 }, global: { plugins: [i18n, router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

describe('TeamDetailView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('should_showMembersWithRoles_when_opened', async () => {
    const wrapper = await mountView('USER', [])

    expect(wrapper.text()).toContain('Alpha')
    expect(wrapper.text()).toContain('Lan')
    expect(wrapper.text()).toContain('Leader')
    expect(wrapper.text()).toContain('Observer (Manager)')
  })

  it('should_hideEditControls_when_notAdmin', async () => {
    const wrapper = await mountView('MANAGER', ['USER_READ'])

    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Add member')
    expect(wrapper.text()).not.toContain('Archive team')
  })

  it('should_showEditControls_when_adminOnActiveTeam', async () => {
    const wrapper = await mountView('ADMIN', ['TEAM_MANAGE'])

    expect(wrapper.text()).toContain('Add member')
    expect(wrapper.text()).toContain('Archive team')
    expect(wrapper.findAll('select')).toHaveLength(3)
  })

  it('should_hideEditControls_when_teamArchived', async () => {
    const wrapper = await mountView('ADMIN', ['TEAM_MANAGE'], team({ status: 'ARCHIVED' }))

    expect(wrapper.text()).toContain('archived')
    expect(wrapper.text()).not.toContain('Add member')
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('should_onlyOfferLeaderOrMember_when_changingAMembersRole', async () => {
    const wrapper = await mountView('ADMIN', ['TEAM_MANAGE'])

    const options = wrapper.findAll('select')[0].findAll('option').map((o) => o.text())
    expect(options).toEqual(['Leader', 'Member'])
    const observerOptions = wrapper.findAll('select')[2].findAll('option').map((o) => o.text())
    expect(observerOptions).toEqual(['Observer (Manager)'])
  })

  it('should_removeMember_when_confirmed', async () => {
    vi.mocked(teamApi.removeMember).mockResolvedValue(team({ members: [] }))
    const wrapper = await mountView('ADMIN', ['TEAM_MANAGE'])

    await wrapper.findAll('button.link.danger')[1].trigger('click')
    const confirm = Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Confirm')
    confirm?.click()
    await flushPromises()

    expect(teamApi.removeMember).toHaveBeenCalledWith(7, 2)
  })

  it('should_showServerError_when_changeRoleRejected', async () => {
    const { ApiError } = await import('../../../shared/types/api')
    vi.mocked(teamApi.changeMemberRole).mockRejectedValue(new ApiError('LAST_LEADER_PROTECTED', 409, 'x'))
    const wrapper = await mountView('ADMIN', ['TEAM_MANAGE'])

    const select = wrapper.findAll('select')[0]
    await select.setValue('MEMBER')
    await flushPromises()

    expect(wrapper.text()).toContain('A team must keep at least one Leader.')
  })
})

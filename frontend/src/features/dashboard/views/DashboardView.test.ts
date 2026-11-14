import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import en from '../../../shared/locales/en.json'
import * as authApi from '../../auth/api'
import { useAuthStore } from '../../auth/store'
import type { SystemRole } from '../../auth/types'
import * as calendarApi from '../../calendar/api'
import { todayKey } from '../../calendar/dates'
import * as notificationApi from '../../notification/api'
import * as reportApi from '../../report/api'
import * as taskApi from '../../task/api'
import * as teamApi from '../../team/api'
import * as userApi from '../../user/api'
import type { Task, TaskStatus } from '../../task/types'
import { addDays } from '../summary'
import DashboardView from './DashboardView.vue'

vi.mock('../../auth/api')
vi.mock('../../task/api')
vi.mock('../../calendar/api')
vi.mock('../../notification/api')
vi.mock('../../report/api')
vi.mock('../../team/api')
vi.mock('../../user/api')

const TODAY = todayKey()
const emptyPage = { items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 }

function task(id: number, dueDate: string | null, status: TaskStatus = 'IN_PROGRESS'): Task {
  return {
    id,
    teamId: 1,
    teamName: 'Alpha',
    title: `Việc ${id}`,
    description: null,
    assignee: { id: 9, fullName: 'Me' },
    assignedBy: null,
    dueDate,
    priority: 'MEDIUM',
    status,
    requireFile: false,
    revisionReason: null,
    version: 0,
    createdAt: '2026-11-01T00:00:00Z',
    submissions: [],
  }
}

async function mountAs(role: SystemRole) {
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
    fullName: 'Lan',
    position: 'Dev',
    systemRole: role,
    status: 'ACTIVE',
    mustChangePassword: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(authApi.fetchPermissions).mockResolvedValue({ systemRole: role, permissions: [] })
  await useAuthStore().login('x@soudan.local', 'pw')

  const stub = { render: () => null }
  const names = ['my-tasks:/tasks/mine', 'activity:/activity', 'communities:/communities', 'calendar:/calendar',
    'chat:/chat', 'task-detail:/tasks/:id', 'member-reports:/c/:id/m/:userId', 'community-feed:/c/:id',
    'team-detail:/teams/:id', 'teams:/teams', 'users:/users', 'home:/']
  const router = createRouter({
    history: createMemoryHistory(),
    routes: names.map((n) => ({ name: n.split(':')[0], path: n.split(':')[1], component: stub })),
  })
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const wrapper = mount(DashboardView, { global: { plugins: [i18n, router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

describe('DashboardView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.mocked(notificationApi.listNotifications).mockResolvedValue(emptyPage)
    vi.mocked(notificationApi.getUnreadCount).mockResolvedValue(0)
    vi.mocked(calendarApi.listNotes).mockResolvedValue([])
    vi.mocked(reportApi.listCommunities).mockResolvedValue([])
    vi.mocked(reportApi.listDaysOff).mockResolvedValue([])
    vi.mocked(taskApi.listMyTasks).mockResolvedValue(emptyPage)
    vi.mocked(teamApi.listTeams).mockResolvedValue(emptyPage)
  })

  it('should_showOverdueAndDueSoonFirst_forMember', async () => {
    vi.mocked(taskApi.listMyTasks).mockResolvedValue({
      ...emptyPage,
      items: [task(1, addDays(TODAY, 20)), task(2, addDays(TODAY, -3)), task(3, addDays(TODAY, 1)), task(4, null, 'DONE')],
    })
    const wrapper = await mountAs('USER')

    const values = wrapper.findAll('[data-testid=stat-value]').map((w) => w.text())
    expect(values[0]).toBe('3') // việc đang làm (không tính đã xong)
    expect(values[1]).toBe('1') // quá hạn
    const titles = wrapper.findAll('[data-testid=attention-task]').map((w) => w.text())
    expect(titles[0]).toContain('Việc 2') // quá hạn lên đầu
    expect(titles[0]).toContain('3 days late')
    expect(titles[1]).toContain('Việc 3')
    expect(titles[1]).toContain('Tomorrow')
  })

  it('should_showReportStatusPerCommunity_andUpcomingNotes', async () => {
    vi.mocked(reportApi.listCommunities).mockResolvedValue([
      { id: 5, name: 'Phòng dev', description: null, owner: null, memberCount: 3, owned: false },
    ])
    vi.mocked(reportApi.getMyToday).mockResolvedValue(null)
    vi.mocked(calendarApi.listNotes).mockResolvedValue([
      { id: 1, date: addDays(TODAY, 2), title: 'Họp 1-1', content: null, color: 'RED' },
    ])
    const wrapper = await mountAs('USER')

    const row = wrapper.find('[data-testid=report-row]')
    expect(row.text()).toContain('Phòng dev')
    expect(row.text()).toContain('Missing')
    expect(wrapper.find('[data-testid=upcoming-note]').text()).toContain('Họp 1-1')
  })

  it('should_markReportSubmitted_orOnLeave', async () => {
    vi.mocked(reportApi.listCommunities).mockResolvedValue([
      { id: 5, name: 'A', description: null, owner: null, memberCount: 3, owned: false },
    ])
    vi.mocked(reportApi.getMyToday).mockResolvedValue({ id: 1 } as never)
    const wrapper = await mountAs('USER')

    expect(wrapper.find('[data-testid=report-row]').text()).toContain('Submitted')
    expect(wrapper.findAll('[data-testid=stat-value]')[2].text()).toBe('1/1')
  })

  it('should_showOwnedCommunitiesProgress_andWhoIsMissing_forManager', async () => {
    vi.mocked(reportApi.listCommunities).mockResolvedValue([
      { id: 5, name: 'Phòng dev', description: null, owner: null, memberCount: 4, owned: true },
      { id: 6, name: 'Không phải của tôi', description: null, owner: null, memberCount: 2, owned: false },
    ])
    vi.mocked(reportApi.getPending).mockResolvedValue({
      date: TODAY,
      workingDay: true,
      missing: [{ id: 21, fullName: 'Bình', position: null }],
      onLeave: [{ id: 22, fullName: 'Chi', position: null }],
    })
    const wrapper = await mountAs('MANAGER')

    const rows = wrapper.findAll('[data-testid=community-row]')
    expect(rows).toHaveLength(1) // chỉ cộng đồng mình quản lý
    expect(rows[0].text()).toContain('2/4 submitted')
    expect(wrapper.find('[data-testid=missing-names]').text()).toContain('Bình')
    expect(wrapper.findAll('[data-testid=stat-value]')[1].text()).toBe('1')
    expect(wrapper.findAll('[data-testid=stat-value]')[2].text()).toBe('1')
  })

  it('should_saySoOnWeekends_forManager', async () => {
    vi.mocked(reportApi.listCommunities).mockResolvedValue([
      { id: 5, name: 'Phòng dev', description: null, owner: null, memberCount: 4, owned: true },
    ])
    vi.mocked(reportApi.getPending).mockResolvedValue({ date: TODAY, workingDay: false, missing: [], onLeave: [] })
    const wrapper = await mountAs('MANAGER')

    expect(wrapper.find('[data-testid=community-row]').text()).toContain('Day off')
  })

  it('should_showAccountAndTeamTotals_forAdmin', async () => {
    vi.mocked(userApi.listUsers).mockImplementation(async (q) => ({
      ...emptyPage,
      totalItems: q.status === 'LOCKED' ? 2 : q.role === 'MANAGER' ? 3 : q.role === 'USER' ? 17 : 21,
    }))
    vi.mocked(teamApi.listTeams).mockImplementation(async (q) => ({ ...emptyPage, totalItems: q.status === 'ARCHIVED' ? 1 : 6 }))
    const wrapper = await mountAs('ADMIN')

    const values = wrapper.findAll('[data-testid=stat-value]').map((w) => w.text())
    expect(values.slice(0, 3)).toEqual(['21', '2', '6'])
    expect(wrapper.text()).toContain('3 managers · 17 members')
  })

  it('should_stillRenderAndWarn_when_oneSourceFails', async () => {
    vi.mocked(taskApi.listMyTasks).mockRejectedValue(new Error('boom'))
    vi.mocked(calendarApi.listNotes).mockResolvedValue([
      { id: 1, date: addDays(TODAY, 1), title: 'Vẫn thấy', content: null, color: 'BLUE' },
    ])
    const wrapper = await mountAs('USER')

    expect(wrapper.find('[data-testid=dashboard-partial]').exists()).toBe(true)
    expect(wrapper.find('[data-testid=upcoming-note]').text()).toContain('Vẫn thấy')
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import en from '../../../shared/locales/en.json'
import { ApiError } from '../../../shared/types/api'
import * as authApi from '../../auth/api'
import { useAuthStore } from '../../auth/store'
import * as teamApi from '../../team/api'
import type { TeamDetail } from '../../team/types'
import * as taskApi from '../api'
import type { HistoryEntry, Task, TaskStatus } from '../types'
import TaskDetailView from './TaskDetailView.vue'

vi.mock('../../auth/api')
vi.mock('../../team/api')
vi.mock('../api')

const ME = 10 // người đang đăng nhập
const OTHER = 20

const team = (myRole: 'LEADER' | 'MEMBER' | 'OBSERVER' | null): TeamDetail => ({
  id: 5,
  name: 'Alpha',
  description: null,
  status: 'ACTIVE',
  version: 1,
  createdAt: '2026-01-01T00:00:00Z',
  members: [
    { userId: OTHER, fullName: 'Other', position: null, teamRole: myRole === 'LEADER' ? 'MEMBER' : 'LEADER', joinedAt: '2026-01-01T00:00:00Z' },
    ...(myRole ? [{ userId: ME, fullName: 'Me', position: null, teamRole: myRole, joinedAt: '2026-01-01T00:00:00Z' }] : []),
  ],
})

const task = (overrides: Partial<Task> = {}): Task => ({
  id: 3,
  teamId: 5,
  teamName: 'Alpha',
  title: 'Viết báo cáo',
  description: 'Chi tiết',
  assignee: { id: ME, fullName: 'Me' },
  assignedBy: { id: OTHER, fullName: 'Other' },
  dueDate: '2030-01-31',
  priority: 'HIGH',
  status: 'ASSIGNED',
  requireFile: false,
  revisionReason: null,
  version: 4,
  createdAt: '2026-01-01T00:00:00Z',
  submissions: [],
  ...overrides,
})

const history: HistoryEntry[] = [
  { id: 1, fromStatus: null, toStatus: 'ASSIGNED', changedBy: { id: OTHER, fullName: 'Other' }, changedAt: '2026-01-01T00:00:00Z', note: null },
  { id: 2, fromStatus: 'PENDING_REVIEW', toStatus: 'IN_PROGRESS', changedBy: { id: OTHER, fullName: 'Other' }, changedAt: '2026-01-02T00:00:00Z', note: 'Thiếu số liệu' },
]

async function mountView(myTeamRole: 'LEADER' | 'MEMBER' | 'OBSERVER' | null, current: Task) {
  vi.mocked(authApi.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: false,
  })
  vi.mocked(authApi.fetchMe).mockResolvedValue({
    id: ME,
    email: 'me@soudan.local',
    fullName: 'Me',
    position: null,
    systemRole: myTeamRole === 'OBSERVER' ? 'MANAGER' : 'USER',
    status: 'ACTIVE',
    mustChangePassword: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(authApi.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
  vi.mocked(taskApi.getTask).mockResolvedValue(current)
  vi.mocked(taskApi.getHistory).mockResolvedValue(history)
  vi.mocked(teamApi.getTeam).mockResolvedValue(team(myTeamRole))
  await useAuthStore().login('me@soudan.local', 'pw')

  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/teams/:id', name: 'team-detail', component: { render: () => null } }],
  })
  const wrapper = mount(TaskDetailView, { props: { id: 3 }, global: { plugins: [i18n, router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

const buttons = (wrapper: Awaited<ReturnType<typeof mountView>>) => wrapper.findAll('button').map((b) => b.text())

describe('TaskDetailView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('should_showDetailAndHistoryTimeline_when_opened', async () => {
    const wrapper = await mountView('MEMBER', task())

    expect(wrapper.text()).toContain('Viết báo cáo')
    expect(wrapper.text()).toContain('Assigned by Other')
    expect(wrapper.text()).toContain('History')
    expect(wrapper.findAll('.timeline li')).toHaveLength(2)
    expect(wrapper.text()).toContain('Thiếu số liệu')
  })

  it.each<[TaskStatus, string[]]>([
    ['ASSIGNED', ['Start']],
    ['IN_PROGRESS', ['Upload file', 'Mark as pending review']],
    ['PENDING_REVIEW', []],
    ['DONE', []],
  ])('should_offerAssigneeActions_when_status_%s', async (status, expected) => {
    const wrapper = await mountView('MEMBER', task({ status }))

    const actions = buttons(wrapper).filter((b) => ['Start', 'Upload file', 'Mark as pending review', 'Approve', 'Send back', 'Edit'].includes(b))
    expect(actions).toEqual(expected)
  })

  it('should_offerApproveAndReturn_when_leaderAndPendingReview', async () => {
    const wrapper = await mountView('LEADER', task({ status: 'PENDING_REVIEW', assignee: { id: OTHER, fullName: 'Other' } }))

    expect(buttons(wrapper)).toEqual(expect.arrayContaining(['Approve', 'Send back', 'Edit']))
    expect(buttons(wrapper)).not.toContain('Start')
  })

  it('should_notOfferApprove_when_notLeader', async () => {
    const wrapper = await mountView('MEMBER', task({ status: 'PENDING_REVIEW' }))

    expect(buttons(wrapper)).not.toContain('Approve')
  })

  it('should_showNoActions_when_observerOrOutsider', async () => {
    for (const role of ['OBSERVER', null] as const) {
      const wrapper = await mountView(role, task({ status: 'PENDING_REVIEW', assignee: { id: OTHER, fullName: 'Other' } }))

      expect(buttons(wrapper).filter((b) => ['Start', 'Approve', 'Send back', 'Edit', 'Upload file'].includes(b))).toEqual([])
      wrapper.unmount()
    }
  })

  it('should_blockSubmit_when_fileRequiredButNoneUploaded', async () => {
    const wrapper = await mountView('MEMBER', task({ status: 'IN_PROGRESS', requireFile: true }))

    const submit = wrapper.findAll('button').find((b) => b.text() === 'Mark as pending review')!
    expect(submit.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Upload a file first.')
  })

  it('should_allowSubmit_when_fileRequiredAndUploaded', async () => {
    const file = { id: 1, fileName: 'a.pdf', contentType: 'application/pdf', sizeBytes: 2048, uploadedBy: ME, uploadedAt: '2026-01-02T00:00:00Z' }
    const wrapper = await mountView('MEMBER', task({ status: 'IN_PROGRESS', requireFile: true, submissions: [file] }))

    const submit = wrapper.findAll('button').find((b) => b.text() === 'Mark as pending review')!
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('should_sendVersion_when_starting', async () => {
    vi.mocked(taskApi.transition).mockResolvedValue(task({ status: 'IN_PROGRESS', version: 5 }))
    const wrapper = await mountView('MEMBER', task())

    await wrapper.findAll('button').find((b) => b.text() === 'Start')!.trigger('click')
    await flushPromises()

    expect(taskApi.transition).toHaveBeenCalledWith(3, 'start', 4)
    expect(wrapper.text()).toContain('In progress')
  })

  it('should_showLightNoticeAndReload_when_someoneElseChangedTheTask', async () => {
    vi.mocked(taskApi.transition).mockRejectedValue(new ApiError('VERSION_CONFLICT', 409, 'x'))
    const wrapper = await mountView('MEMBER', task())

    await wrapper.findAll('button').find((b) => b.text() === 'Start')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Someone just changed this task')
    expect(taskApi.getTask).toHaveBeenCalledTimes(2)
  })

  it('should_requireReasonBeforeSendingBack', async () => {
    vi.mocked(taskApi.requestRevision).mockResolvedValue(task({ status: 'IN_PROGRESS' }))
    const wrapper = await mountView('LEADER', task({ status: 'PENDING_REVIEW', assignee: { id: OTHER, fullName: 'Other' } }))

    await wrapper.findAll('button').find((b) => b.text() === 'Send back')!.trigger('click')
    const send = Array.from(document.body.querySelectorAll('button')).filter((b) => b.textContent?.trim() === 'Send back').pop()!
    expect(send.disabled).toBe(true)

    const area = document.body.querySelector('textarea')!
    area.value = 'Thiếu số liệu'
    area.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(send.disabled).toBe(false)
    send.click()
    await flushPromises()

    expect(taskApi.requestRevision).toHaveBeenCalledWith(3, 4, 'Thiếu số liệu')
  })

  it('should_rejectBadFileLocally_withoutCallingServer', async () => {
    const wrapper = await mountView('MEMBER', task({ status: 'IN_PROGRESS' }))
    const input = wrapper.find('[data-testid="file-input"]')

    Object.defineProperty(input.element, 'files', { value: [new File(['x'], 'run.exe')], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.text()).toContain('Only PDF, Word, Excel, image and zip files are accepted.')
    expect(taskApi.uploadSubmission).not.toHaveBeenCalled()

    const big = new File(['x'], 'big.pdf')
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 })
    Object.defineProperty(input.element, 'files', { value: [big], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.text()).toContain('larger than 10 MB')
    expect(taskApi.uploadSubmission).not.toHaveBeenCalled()
  })

  it('should_uploadAndRefresh_when_fileIsValid', async () => {
    vi.mocked(taskApi.uploadSubmission).mockResolvedValue({ id: 9, fileName: 'a.pdf', contentType: 'application/pdf', sizeBytes: 1, uploadedBy: ME, uploadedAt: '2026-01-02T00:00:00Z' })
    const wrapper = await mountView('MEMBER', task({ status: 'IN_PROGRESS' }))
    const input = wrapper.find('[data-testid="file-input"]')

    Object.defineProperty(input.element, 'files', { value: [new File(['%PDF'], 'a.pdf')], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(taskApi.uploadSubmission).toHaveBeenCalledOnce()
    expect(taskApi.getTask).toHaveBeenCalledTimes(2)
  })

  it('should_showDownloadLinks_onlyToAssigneeAndLeader', async () => {
    const file = { id: 1, fileName: 'a.pdf', contentType: 'application/pdf', sizeBytes: 2048, uploadedBy: ME, uploadedAt: '2026-01-02T00:00:00Z' }

    const mine = await mountView('MEMBER', task({ status: 'IN_PROGRESS', submissions: [file] }))
    expect(mine.find('.files button.link').exists()).toBe(true)
    mine.unmount()

    const observer = await mountView('OBSERVER', task({ status: 'IN_PROGRESS', assignee: { id: OTHER, fullName: 'Other' }, submissions: [file] }))
    expect(observer.find('.files button.link').exists()).toBe(false)
    expect(observer.text()).toContain('a.pdf')
  })
})

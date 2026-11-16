import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import en from '../../shared/locales/en.json'
import { ApiError } from '../../shared/types/api'
import { installGuards } from '../../app/guards'
import * as authApi from '../auth/api'
import * as api from './api'
import { loadHostSession, saveHostSession } from './hostSession'
import NewMeetingButton from './components/NewMeetingButton.vue'
import MeetingRoomView from './views/MeetingRoomView.vue'

vi.mock('./api')
vi.mock('../auth/api')

const session = { room: 'abcdefghijkm2345', appId: 'vpaas-magic-cookie-x', domain: '8x8.vc', jwt: 'jwt.token.value' }
const blank = { render: () => null }

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: blank },
      { path: '/login', name: 'login', component: blank, meta: { guestOnly: true } },
      { path: '/meet/:room', name: 'meeting', component: MeetingRoomView, meta: { public: true } },
    ],
  })
  installGuards(router)
  return router
}

const i18n = () => createI18n({ legacy: false, locale: 'en', messages: { en } })

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  localStorage.clear()
  // không có phiên đăng nhập: restore() kết thúc êm
  vi.mocked(authApi.fetchMe).mockRejectedValue(new Error('no session'))
})

describe('hostSession', () => {
  it('nhớ token chủ trì để tải lại trang vẫn là chủ trì', () => {
    saveHostSession(session)
    expect(loadHostSession(session.room)).toEqual(session)
    expect(loadHostSession('khac')).toBeNull()
  })

  it('bỏ token quá hạn', () => {
    saveHostSession(session)
    vi.setSystemTime(Date.now() + 4 * 60 * 60 * 1000)
    expect(loadHostSession(session.room)).toBeNull()
    vi.useRealTimers()
  })
})

describe('NewMeetingButton', () => {
  async function mountButton() {
    const router = makeRouter()
    await router.push('/')
    await router.isReady()
    const wrapper = mount(NewMeetingButton, { global: { plugins: [router, i18n()] } })
    return { router, wrapper }
  }

  it('tạo phòng rồi mở trang họp với tư cách chủ trì', async () => {
    vi.mocked(api.createMeeting).mockResolvedValue(session)
    const { router, wrapper } = await mountButton()

    await wrapper.get('[data-testid="new-meeting"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('meeting')
    expect(router.currentRoute.value.params.room).toBe(session.room)
    expect(loadHostSession(session.room)?.jwt).toBe(session.jwt)
  })

  it('báo rõ khi họp video chưa được cấu hình và ở lại trang hiện tại', async () => {
    vi.mocked(api.createMeeting).mockRejectedValue(new ApiError('MEETING_NOT_CONFIGURED', 503, ''))
    const { router, wrapper } = await mountButton()

    await wrapper.get('[data-testid="new-meeting"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="new-meeting-error"]').text()).toContain('not configured')
    expect(router.currentRoute.value.name).not.toBe('meeting')
    expect(api.createMeeting).toHaveBeenCalledTimes(1)
  })
})

describe('MeetingRoomView', () => {
  it('cho khách chưa đăng nhập vào trang họp (route công khai) và hỏi tên trước', async () => {
    const router = makeRouter()
    await router.push(`/meet/${session.room}`)
    await router.isReady()
    const wrapper = mount(MeetingRoomView, { global: { plugins: [router, i18n()] } })
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('meeting')
    expect(wrapper.find('[data-testid="meeting-prejoin"]').exists()).toBe(true)
  })

  it('báo lỗi dễ hiểu khi họp video chưa cấu hình, không treo ở màn hình chờ', async () => {
    vi.mocked(api.joinMeeting).mockRejectedValue(new ApiError('MEETING_NOT_CONFIGURED', 503, ''))
    const router = makeRouter()
    await router.push(`/meet/${session.room}`)
    await router.isReady()
    const wrapper = mount(MeetingRoomView, { global: { plugins: [router, i18n()] } })
    await flushPromises()

    await wrapper.get('#meeting-name').setValue('Khách A')
    await wrapper.get('[data-testid="meeting-prejoin"]').trigger('submit')
    await flushPromises()

    expect(api.joinMeeting).toHaveBeenCalledWith(session.room, 'Khách A')
    expect(wrapper.get('[data-testid="meeting-error"]').text()).toContain('not configured')
  })
})

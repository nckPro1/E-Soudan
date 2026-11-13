import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import en from '../../../shared/locales/en.json'
import * as calendarApi from '../api'
import { todayKey } from '../dates'
import type { CalendarNote } from '../types'
import CalendarView from './CalendarView.vue'

vi.mock('../api')

const note = (overrides: Partial<CalendarNote> = {}): CalendarNote => ({
  id: 1,
  date: todayKey(),
  title: 'Nộp báo cáo',
  content: 'Trước 17h',
  color: 'RED',
  ...overrides,
})

async function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const wrapper = mount(CalendarView, { global: { plugins: [i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

describe('CalendarView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('should_showNotesAsChipsAndInPanel_when_dayHasNotes', async () => {
    vi.mocked(calendarApi.listNotes).mockResolvedValue([note()])

    const wrapper = await mountView()

    expect(wrapper.find('[data-testid=note-chip]').text()).toBe('Nộp báo cáo')
    // Hôm nay đang được chọn nên bảng bên phải hiện ghi chú của hôm nay
    expect(wrapper.find('[data-testid=day-panel]').text()).toContain('Trước 17h')
  })

  it('should_createNoteForSelectedDay_when_formSaved', async () => {
    vi.mocked(calendarApi.listNotes).mockResolvedValue([])
    vi.mocked(calendarApi.createNote).mockResolvedValue(note({ id: 9, title: 'Họp', content: null, color: 'GREEN' }))
    const wrapper = await mountView()

    await wrapper.find('[data-testid=add-note]').trigger('click')
    await wrapper.find('[data-testid=note-title]').setValue('Họp')
    await wrapper.find('[role=radio][aria-label=Green]').trigger('click')
    await wrapper.find('[data-testid=note-form]').trigger('submit')
    await flushPromises()

    expect(calendarApi.createNote).toHaveBeenCalledWith({ date: todayKey(), title: 'Họp', content: null, color: 'GREEN' })
    expect(wrapper.findAll('[data-testid=note-item]')).toHaveLength(1)
  })

  it('should_removeNote_when_deleteClicked', async () => {
    vi.mocked(calendarApi.listNotes).mockResolvedValue([note()])
    vi.mocked(calendarApi.deleteNote).mockResolvedValue()
    const wrapper = await mountView()

    await wrapper.find('[data-testid=note-delete]').trigger('click')
    await flushPromises()

    expect(calendarApi.deleteNote).toHaveBeenCalledWith(1)
    expect(wrapper.find('[data-testid=day-empty]').exists()).toBe(true)
  })

  it('should_reloadRange_when_monthChanged', async () => {
    vi.mocked(calendarApi.listNotes).mockResolvedValue([])
    const wrapper = await mountView()

    await wrapper.find('[data-testid=cal-next]').trigger('click')
    await flushPromises()

    expect(calendarApi.listNotes).toHaveBeenCalledTimes(2)
  })
})

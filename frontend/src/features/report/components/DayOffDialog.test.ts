import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { ApiError } from '../../../shared/types/api'
import en from '../../../shared/locales/en.json'
import * as reportApi from '../api'
import DayOffDialog from './DayOffDialog.vue'

vi.mock('../api')

function mountDialog() {
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  return mount(DayOffDialog, { global: { plugins: [i18n] }, attachTo: document.body })
}

describe('DayOffDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('should_listUpcomingDaysOff_when_opened', async () => {
    vi.mocked(reportApi.listDaysOff).mockResolvedValue([{ day: '2030-02-04', reason: 'Trip' }])
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.findAll('[data-testid=dayoff-item]')).toHaveLength(1)
    expect(wrapper.text()).toContain('Trip')
  })

  it('should_markDayOffWithReason_andReload', async () => {
    vi.mocked(reportApi.listDaysOff).mockResolvedValue([])
    vi.mocked(reportApi.markDayOff).mockResolvedValue({ day: '2030-03-01', reason: 'Family' })
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.find('[data-testid=dayoff-date]').setValue('2030-03-01')
    await wrapper.find('[data-testid=dayoff-reason]').setValue('Family')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(reportApi.markDayOff).toHaveBeenCalledWith('2030-03-01', 'Family')
    expect(reportApi.listDaysOff).toHaveBeenCalledTimes(2)
  })

  it('should_removeDayOff_when_deleteClicked', async () => {
    vi.mocked(reportApi.listDaysOff).mockResolvedValue([{ day: '2030-02-04', reason: null }])
    vi.mocked(reportApi.unmarkDayOff).mockResolvedValue()
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.find('[data-testid=dayoff-item] button').trigger('click')
    await flushPromises()

    expect(reportApi.unmarkDayOff).toHaveBeenCalledWith('2030-02-04')
  })

  it('should_showTranslatedError_when_serverRejects', async () => {
    vi.mocked(reportApi.listDaysOff).mockResolvedValue([])
    vi.mocked(reportApi.markDayOff).mockRejectedValue(new ApiError('VALIDATION_ERROR', 422, 'bad'))
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain(en.errors.VALIDATION_ERROR)
  })
})

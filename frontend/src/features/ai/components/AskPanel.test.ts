import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiError } from '../../../shared/types/api'
import en from '../../../shared/locales/en.json'
import * as aiApi from '../api'
import AskPanel from './AskPanel.vue'

vi.mock('../api')

function mountPanel() {
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { render: () => null } }] })
  return mount(AskPanel, { global: { plugins: [i18n, router] }, attachTo: document.body })
}

describe('AskPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('should_askAndShowAnswerWithSources_when_submitted', async () => {
    vi.mocked(aiApi.ask).mockResolvedValue({
      text: 'Payment is done [1].',
      sources: [{ n: 1, kind: 'TASK', title: 'Công việc: Thanh toán', path: '/tasks/5', at: null }],
    })
    const wrapper = mountPanel()

    await wrapper.find('[data-testid=ask-input]').setValue('How is payment?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(aiApi.ask).toHaveBeenCalledWith('How is payment?', [], 'en')
    expect(wrapper.text()).toContain('How is payment?')
    expect(wrapper.text()).toContain('Payment is done')
    expect(wrapper.find('[data-testid=ai-cite]').exists()).toBe(true)
  })

  it('should_sendPreviousTurnsAsHistory_when_askingFollowUp', async () => {
    vi.mocked(aiApi.ask).mockResolvedValue({ text: 'First answer.', sources: [] })
    const wrapper = mountPanel()
    await wrapper.find('[data-testid=ask-input]').setValue('First?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    await wrapper.find('[data-testid=ask-input]').setValue('And then?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(aiApi.ask).toHaveBeenLastCalledWith(
      'And then?',
      [
        { role: 'user', text: 'First?' },
        { role: 'assistant', text: 'First answer.' },
      ],
      'en',
    )
  })

  it('should_showTranslatedError_when_aiFails', async () => {
    vi.mocked(aiApi.ask).mockRejectedValue(new ApiError('AI_RATE_LIMITED', 429, 'limit'))
    const wrapper = mountPanel()

    await wrapper.find('[data-testid=ask-input]').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('too many times')
  })

  it('should_notSend_when_questionBlank', async () => {
    const wrapper = mountPanel()

    await wrapper.find('[data-testid=ask-input]').setValue('   ')
    await wrapper.find('form').trigger('submit')

    expect(aiApi.ask).not.toHaveBeenCalled()
  })
})

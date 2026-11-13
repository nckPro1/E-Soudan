import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import en from '../../../shared/locales/en.json'
import type { AiAnswer } from '../types'
import AiAnswerBody from './AiAnswerBody.vue'

function mountBody(answer: AiAnswer) {
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { render: () => null } }] })
  return mount(AiAnswerBody, { props: { answer }, global: { plugins: [i18n, router] } })
}

describe('AiAnswerBody', () => {
  it('should_turnKnownCitationsIntoLinksAndDropUnknownOnes', () => {
    const wrapper = mountBody({
      text: 'Done the payment work [1] and something [7].',
      sources: [{ n: 1, kind: 'REPORT', title: 'Báo cáo 13/11', path: '/communities/3/members/9', at: null }],
    })

    const cites = wrapper.findAll('[data-testid=ai-cite]')
    expect(cites).toHaveLength(1)
    expect(cites[0].text()).toBe('1')
    expect(cites[0].attributes('href')).toBe('/communities/3/members/9')
    expect(wrapper.text()).not.toContain('[7]')
    expect(wrapper.text()).toContain('Báo cáo 13/11')
  })

  it('should_renderTextAsPlainText_when_answerContainsHtml', () => {
    const wrapper = mountBody({ text: '<img src=x onerror=alert(1)> hello', sources: [] })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)> hello')
  })

  it('should_showNoSourceList_when_nothingCited', () => {
    const wrapper = mountBody({ text: 'Nothing found.', sources: [] })

    expect(wrapper.find('[data-testid=ai-sources]').exists()).toBe(false)
  })
})

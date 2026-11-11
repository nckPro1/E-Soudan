import { createI18n } from 'vue-i18n'
import vi from '../../shared/locales/vi.json'

export const i18n = createI18n({
  legacy: false,
  locale: 'vi',
  fallbackLocale: 'vi',
  messages: { vi },
})

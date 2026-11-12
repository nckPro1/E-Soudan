import { createI18n } from 'vue-i18n'
import en from '../../shared/locales/en.json'
import vi from '../../shared/locales/vi.json'
import ja from '../../shared/locales/ja.json'

export const LOCALES = ['ja', 'vi', 'en'] as const
export type Locale = (typeof LOCALES)[number]

/** Nhãn ngôn ngữ luôn hiển thị bằng chính ngôn ngữ đó (như thiết kế). */
export const LOCALE_LABELS: Record<Locale, string> = {
  ja: '日本語',
  vi: 'Tiếng Việt',
  en: 'English',
}

const STORAGE_KEY = 'soudan.locale'
const DEFAULT_LOCALE: Locale = 'vi'

function readStoredLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored && (LOCALES as readonly string[]).includes(stored)) {
      return stored as Locale
    }
  } catch {
    // localStorage không dùng được (chế độ riêng tư...): dùng mặc định
  }
  return DEFAULT_LOCALE
}

export function createAppI18n() {
  const locale = readStoredLocale()
  document.documentElement.lang = locale
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, vi, ja },
  })
}

export const i18n = createAppI18n()

export function setLocale(locale: Locale): void {
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // bỏ qua: chỉ mất việc ghi nhớ lựa chọn
  }
}

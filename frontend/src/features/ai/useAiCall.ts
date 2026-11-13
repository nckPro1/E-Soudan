import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toApiError } from '../../shared/api/http'
import type { AiAnswer, AiLang } from './types'

/** Ngôn ngữ trả lời của AI theo ngôn ngữ đang chọn của ứng dụng (mặc định tiếng Việt). */
export function useAiLang() {
  const { locale } = useI18n()
  return computed<AiLang>(() => (locale.value === 'ja' || locale.value === 'en' ? locale.value : 'vi'))
}

/** Một lần gọi AI: trạng thái đang chạy, kết quả và thông báo lỗi dịch sẵn. Gọi lại thì thay kết quả cũ. */
export function useAiCall() {
  const { t, te } = useI18n()
  const loading = ref(false)
  const answer = ref<AiAnswer | null>(null)
  const errorCode = ref('')

  const errorMessage = computed(() => {
    if (!errorCode.value) return ''
    return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
  })

  async function run(call: () => Promise<AiAnswer>): Promise<AiAnswer | null> {
    if (loading.value) return null
    loading.value = true
    errorCode.value = ''
    answer.value = null
    try {
      answer.value = await call()
      return answer.value
    } catch (error) {
      errorCode.value = toApiError(error).code
      return null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    answer.value = null
    errorCode.value = ''
  }

  return { loading, answer, errorCode, errorMessage, run, reset }
}

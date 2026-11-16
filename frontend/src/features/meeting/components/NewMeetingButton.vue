<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import { createMeeting } from '../api'
import { saveHostSession } from '../hostSession'

/**
 * Nút camera "Cuộc họp mới" ở thanh trên, cạnh ảnh đại diện. Tạo phòng (người bấm là chủ trì) rồi mở trang họp;
 * lỗi (ví dụ chưa cấu hình họp video) hiện một dòng nhỏ dưới nút rồi tự mất.
 */
const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const busy = ref(false)
const message = ref('')
let timer: ReturnType<typeof setTimeout> | undefined

function showMessage(text: string) {
  message.value = text
  clearTimeout(timer)
  timer = setTimeout(() => (message.value = ''), 4000)
}

onBeforeUnmount(() => clearTimeout(timer))

async function start() {
  if (busy.value) return
  busy.value = true
  try {
    const session = await createMeeting(auth.user?.fullName ?? '')
    saveHostSession(session)
    await router.push({ name: 'meeting', params: { room: session.room } })
  } catch (error) {
    showMessage(toApiError(error).code === 'MEETING_NOT_CONFIGURED' ? t('meeting.notConfigured') : t('meeting.failed'))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="relative">
    <button
      type="button"
      class="flex h-7 w-7 items-center justify-center rounded text-[#616161] hover:bg-black/5 hover:text-black disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-[#4f52b2]"
      :aria-label="t('meeting.new')"
      :title="t('meeting.new')"
      :disabled="busy"
      data-testid="new-meeting"
      @click="start"
    >
      <svg
        class="h-5 w-5"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M3 7a2 2 0 012-2h8a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2zM15 10l6-3v10l-6-3" />
      </svg>
    </button>
    <p
      v-if="message"
      class="absolute right-0 top-9 z-20 w-56 rounded bg-[#242424] px-3 py-2 text-xs text-white shadow-lg"
      role="alert"
      data-testid="new-meeting-error"
    >
      {{ message }}
    </p>
  </div>
</template>

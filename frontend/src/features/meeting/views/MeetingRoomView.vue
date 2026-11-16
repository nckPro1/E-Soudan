<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import { joinMeeting } from '../api'
import { loadHostSession } from '../hostSession'
import { loadJitsi, type JitsiApi } from '../jitsi'
import type { MeetingSession } from '../types'

/**
 * Phòng họp (công khai, không nằm trong khung ứng dụng). Chủ trì (vừa tạo phòng) vào thẳng và bật phòng chờ; ai có link
 * thì nhập tên, nhận token khách rồi chờ chủ trì cho vào. Khung họp là Jitsi (JaaS) nhúng bằng IFrame API.
 */
const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const room = computed(() => String(route.params.room ?? ''))
const shareLink = computed(() => `${window.location.origin}/meet/${room.value}`)

type Phase = 'loading' | 'prejoin' | 'joining' | 'live' | 'error'
const phase = ref<Phase>('loading')
const errorKey = ref('meeting.failed')
const name = ref(auth.user?.fullName ?? '')
const copied = ref(false)
const container = ref<HTMLElement | null>(null)
let api: JitsiApi | null = null

onMounted(async () => {
  await auth.restore()
  if (!name.value) name.value = auth.user?.fullName ?? ''
  const host = loadHostSession(room.value)
  if (host) {
    await start(host, true)
  } else {
    phase.value = 'prejoin'
  }
})

onBeforeUnmount(() => {
  api?.dispose()
  api = null
})

function fail(error: unknown) {
  const code = toApiError(error).code
  errorKey.value =
    code === 'MEETING_NOT_CONFIGURED'
      ? 'meeting.notConfigured'
      : code === 'MEETING_RATE_LIMITED'
        ? 'meeting.rateLimited'
        : code === 'INVALID_MEETING_ROOM'
          ? 'meeting.invalidRoom'
          : 'meeting.failed'
  phase.value = 'error'
}

async function submitName() {
  const trimmed = name.value.trim()
  if (!trimmed || phase.value === 'joining') return
  phase.value = 'joining'
  try {
    await start(await joinMeeting(room.value, trimmed), false)
  } catch (error) {
    fail(error)
  }
}

async function start(session: MeetingSession, moderator: boolean) {
  try {
    const Jitsi = await loadJitsi(session.domain, session.appId)
    phase.value = 'live'
    if (!container.value) return
    api = new Jitsi(session.domain, {
      roomName: `${session.appId}/${session.room}`,
      jwt: session.jwt,
      parentNode: container.value,
      width: '100%',
      height: '100%',
      lang: locale.value,
    })
    // Chủ trì bật phòng chờ ngay khi vào: người có link phải chờ được duyệt mới vào được.
    if (moderator) {
      api.addListener('videoConferenceJoined', () => api?.executeCommand('toggleLobby', true))
    }
    api.addListener('readyToClose', leave)
  } catch (error) {
    fail(error)
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(shareLink.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    window.prompt(t('meeting.copyLink'), shareLink.value)
  }
}

async function leave() {
  api?.dispose()
  api = null
  await router.replace(auth.isAuthenticated ? { name: 'home' } : { name: 'login' })
}
</script>

<template>
  <div
    class="flex h-screen w-full flex-col bg-[#1f1f1f] text-white"
    data-testid="meeting-room"
  >
    <header class="flex h-11 shrink-0 items-center justify-between gap-3 bg-[#2b2b2b] px-3 text-xs">
      <span class="font-semibold">{{ t('meeting.title') }}</span>
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="rounded bg-white/10 px-3 py-1 hover:bg-white/20"
          data-testid="meeting-copy"
          @click="copyLink"
        >
          {{ copied ? t('meeting.copied') : t('meeting.copyLink') }}
        </button>
        <button
          type="button"
          class="rounded bg-[#c4314b] px-3 py-1 hover:brightness-110"
          data-testid="meeting-leave"
          @click="leave"
        >
          {{ t('meeting.leave') }}
        </button>
      </div>
    </header>

    <main class="relative min-h-0 flex-1">
      <div
        ref="container"
        class="h-full w-full"
        :class="{ invisible: phase !== 'live' }"
      />

      <div
        v-if="phase !== 'live'"
        class="absolute inset-0 flex items-center justify-center p-4"
      >
        <p
          v-if="phase === 'loading'"
          class="text-sm text-white/70"
        >
          {{ t('meeting.loading') }}
        </p>

        <form
          v-else-if="phase === 'prejoin' || phase === 'joining'"
          class="w-full max-w-sm space-y-3 rounded-lg bg-white p-5 text-[#242424] shadow-lg"
          data-testid="meeting-prejoin"
          @submit.prevent="submitName"
        >
          <h1 class="text-base font-semibold">
            {{ t('meeting.joinTitle') }}
          </h1>
          <p class="text-xs text-[#616161]">
            {{ t('meeting.joinHint') }}
          </p>
          <label
            class="block text-xs font-medium"
            for="meeting-name"
          >{{ t('meeting.yourName') }}</label>
          <input
            id="meeting-name"
            v-model="name"
            class="w-full rounded border border-[#d1d1d1] px-3 py-2 text-sm outline-none focus:border-[#4f52b2]"
            maxlength="60"
            autocomplete="name"
            required
          >
          <button
            type="submit"
            class="w-full rounded bg-[#4f52b2] px-3 py-2 text-sm font-semibold text-white hover:brightness-110 disabled:opacity-50"
            :disabled="phase === 'joining' || !name.trim()"
          >
            {{ phase === 'joining' ? t('meeting.joining') : t('meeting.join') }}
          </button>
        </form>

        <p
          v-else
          class="max-w-sm rounded bg-white/10 p-4 text-center text-sm"
          role="alert"
          data-testid="meeting-error"
        >
          {{ t(errorKey) }}
        </p>
      </div>
    </main>
  </div>
</template>

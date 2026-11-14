<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthLayout from '../../../shared/components/AuthLayout.vue'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import SubmitButton from '../../../shared/components/SubmitButton.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import * as authApi from '../api'
import DemoAccountDialog from '../components/DemoAccountDialog.vue'
import { formatDays, statusAt } from '../operatingHours'
import { safeRedirect } from '../redirect'
import { useAuthStore } from '../store'
import type { DemoAccount, OperatingHours } from '../types'

/**
 * Năm trạng thái theo thiết kế Stitch:
 * Default (idle, chưa banner) · Loading · Error (sai thông tin) · Account Locked · Session Expired.
 */
type Status = 'idle' | 'loading' | 'error' | 'locked'

const { t, te, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const sessionExpired = route.query.reason === 'expired'

const email = ref(sessionExpired ? auth.lastEmail : '')
const password = ref('')
const status = ref<Status>('idle')
const errorCode = ref('')

/** Tài khoản demo (null: máy chủ không bật demo nên không hiện nút). */
const demo = ref<DemoAccount | null>(null)
const showDemo = ref(false)

/** Giờ hoạt động của hệ thống (null: máy chạy cả ngày nên không hiện). Trạng thái mở/đóng được tính lại mỗi 30 giây. */
const hours = ref<OperatingHours | null>(null)
const nowTick = ref(new Date())
let tick: ReturnType<typeof setInterval> | undefined
const hoursStatus = computed(() => (hours.value ? statusAt(hours.value, nowTick.value) : null))
const daysText = computed(() => (hours.value ? formatDays(hours.value.days, locale.value) : ''))
const zoneText = computed(() =>
  hours.value?.timezone === 'Asia/Ho_Chi_Minh' ? t('auth.hours.vn') : (hours.value?.timezone ?? ''),
)
const closesIn = computed(() => {
  const left = hoursStatus.value?.minutesLeft
  if (left === null || left === undefined || left > 120) return ''
  return left >= 60 ? t('auth.hours.hm', { h: Math.floor(left / 60), m: left % 60 }) : t('auth.hours.m', { m: left })
})

onBeforeUnmount(() => clearInterval(tick))

onMounted(async () => {
  tick = setInterval(() => (nowTick.value = new Date()), 30_000)
  try {
    hours.value = (await authApi.getPublicInfo())?.operatingHours ?? null
  } catch {
    hours.value = null // không lấy được thì thôi, trang đăng nhập vẫn dùng bình thường
  }
  try {
    const account = await authApi.getDemoAccount()
    demo.value = account?.enabled ? account : null
  } catch {
    demo.value = null // không lấy được thì thôi, trang đăng nhập vẫn dùng bình thường
  }
})

function fillDemo(demoEmail: string, demoPassword: string) {
  email.value = demoEmail
  password.value = demoPassword
  errorCode.value = ''
  status.value = 'idle'
  showDemo.value = false
}

const loading = computed(() => status.value === 'loading')
const locked = computed(() => status.value === 'locked')
const showExpired = computed(() => sessionExpired && status.value === 'idle')
const showError = computed(() => status.value === 'error' || locked.value)
const hasBanner = computed(() => showError.value || showExpired.value)
const invalidCredentials = computed(() => status.value === 'error' && errorCode.value === 'INVALID_CREDENTIALS')
const errorMessage = computed(() =>
  te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN'),
)

async function submit() {
  if (loading.value || locked.value) {
    return
  }
  status.value = 'loading'
  errorCode.value = ''
  try {
    await auth.login(email.value.trim(), password.value)
    // Nếu cần đổi mật khẩu, router guard sẽ chuyển tiếp sang trang đổi mật khẩu.
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    errorCode.value = toApiError(error).code
    status.value = errorCode.value === 'ACCOUNT_LOCKED' ? 'locked' : 'error'
  }
}

function useAnotherAccount() {
  email.value = ''
  password.value = ''
  errorCode.value = ''
  status.value = 'idle'
}
</script>

<template>
  <AuthLayout>
    <h1
      class="title flex items-baseline gap-2 text-base font-medium"
      :class="hasBanner ? 'tight mb-4' : 'mb-6'"
    >
      <span>{{ t('auth.signIn') }}</span>
      <span
        v-if="t('auth.signInSub')"
        class="sub text-xs font-normal text-[#616161]"
      >{{ t('auth.signInSub') }}</span>
    </h1>

    <AlertBanner
      v-if="showError"
      tone="error"
    >
      {{ errorMessage }}
    </AlertBanner>
    <AlertBanner
      v-else-if="showExpired"
      tone="info"
    >
      {{ t('auth.sessionExpired') }}
    </AlertBanner>

    <form
      class="form flex flex-col gap-6"
      @submit.prevent="submit"
    >
      <TextField
        id="email"
        v-model="email"
        type="email"
        :label="t('auth.email')"
        placeholder="name@company.co.jp"
        autocomplete="email"
        :disabled="loading || locked"
        :invalid="invalidCredentials"
        required
      />
      <TextField
        id="password"
        v-model="password"
        type="password"
        :label="t('auth.password')"
        placeholder="••••••••••••"
        autocomplete="current-password"
        :disabled="loading || locked"
        :invalid="invalidCredentials"
        :autofocus="showExpired"
        required
      />
      <SubmitButton
        :loading="loading"
        :loading-text="t('auth.signingIn')"
        :disabled="locked"
      >
        {{ t('auth.signIn') }}
      </SubmitButton>
    </form>

    <div
      v-if="demo"
      class="demo mt-5 border-t border-[#edebe9] pt-4 text-center"
    >
      <button
        type="button"
        class="btn w-full"
        :disabled="loading"
        data-testid="demo-open"
        @click="showDemo = true"
      >
        {{ t('auth.demo.button') }}
      </button>
      <p class="mt-1.5 text-xs text-[#616161]">
        {{ t('auth.demo.hint') }}
      </p>
    </div>

    <div
      v-if="hours && hoursStatus"
      class="hours mt-5 rounded-lg bg-[#f5f5f5] px-3 py-2.5 text-xs leading-relaxed text-[#424242]"
      data-testid="operating-hours"
    >
      <p class="font-semibold text-[#242424]">
        {{ t('auth.hours.title', { start: hours.start, end: hours.end }) }}
      </p>
      <p class="text-[#616161]">
        {{ t('auth.hours.line', { days: daysText, tz: zoneText }) }}
      </p>
      <p
        v-if="closesIn"
        class="mt-1 font-semibold text-[#ca5010]"
        data-testid="hours-closing"
      >
        {{ t('auth.hours.closesIn', { time: closesIn }) }}
      </p>
      <p
        v-else-if="!hoursStatus.open"
        class="mt-1 font-semibold text-[#c4314b]"
        data-testid="hours-closed"
      >
        {{ t('auth.hours.closedNow') }}
      </p>
    </div>

    <div class="support mt-6 text-center">
      <p class="text-xs leading-relaxed text-[#616161]">
        {{ t('auth.support') }}
      </p>
      <button
        v-if="locked"
        type="button"
        class="link mt-2 cursor-pointer border-0 bg-transparent p-0 text-xs text-[#4f52b2] underline"
        @click="useAnotherAccount"
      >
        {{ t('auth.useAnotherAccount') }}
      </button>
    </div>
    <DemoAccountDialog
      v-if="showDemo && demo"
      :account="demo"
      @close="showDemo = false"
      @fill="fillDemo"
    />
  </AuthLayout>
</template>

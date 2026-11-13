<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthLayout from '../../../shared/components/AuthLayout.vue'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import SubmitButton from '../../../shared/components/SubmitButton.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import { safeRedirect } from '../redirect'
import { useAuthStore } from '../store'

/**
 * Năm trạng thái theo thiết kế Stitch:
 * Default (idle, chưa banner) · Loading · Error (sai thông tin) · Account Locked · Session Expired.
 */
type Status = 'idle' | 'loading' | 'error' | 'locked'

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const sessionExpired = route.query.reason === 'expired'

const email = ref(sessionExpired ? auth.lastEmail : '')
const password = ref('')
const status = ref<Status>('idle')
const errorCode = ref('')

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
  </AuthLayout>
</template>

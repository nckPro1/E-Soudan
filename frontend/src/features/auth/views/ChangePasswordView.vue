<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthLayout from '../../../shared/components/AuthLayout.vue'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import SubmitButton from '../../../shared/components/SubmitButton.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../store'

const MIN_LENGTH = 8

const { t, te } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const current = ref('')
const next = ref('')
const confirm = ref('')
const saving = ref(false)
const errorKey = ref('')

const forced = computed(() => auth.mustChangePassword)
const errorMessage = computed(() => {
  if (!errorKey.value) {
    return ''
  }
  if (errorKey.value === 'mismatch') {
    return t('changePassword.mismatch')
  }
  return te(`errors.${errorKey.value}`) ? t(`errors.${errorKey.value}`) : t('errors.UNKNOWN')
})

async function submit() {
  if (saving.value) {
    return
  }
  errorKey.value = ''
  if (next.value !== confirm.value) {
    errorKey.value = 'mismatch'
    return
  }
  if (next.value.length < MIN_LENGTH) {
    errorKey.value = 'VALIDATION_ERROR'
    return
  }
  saving.value = true
  try {
    await auth.changePassword(current.value, next.value)
    await router.replace({ name: 'home' })
  } catch (error) {
    errorKey.value = toApiError(error).code
  } finally {
    saving.value = false
  }
}

async function signOut() {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <AuthLayout>
    <h1
      class="title flex items-baseline gap-2 text-base font-medium"
      :class="forced || errorMessage ? 'tight mb-4' : 'mb-6'"
    >
      <span>{{ t('changePassword.title') }}</span>
      <span
        v-if="t('changePassword.titleSub')"
        class="sub text-xs font-normal text-[#616161]"
      >{{ t('changePassword.titleSub') }}</span>
    </h1>

    <AlertBanner
      v-if="errorMessage"
      tone="error"
    >
      {{ errorMessage }}
    </AlertBanner>
    <AlertBanner
      v-else-if="forced"
      tone="info"
    >
      {{ t('changePassword.required') }}
    </AlertBanner>

    <form
      class="form flex flex-col gap-6"
      @submit.prevent="submit"
    >
      <TextField
        id="current-password"
        v-model="current"
        type="password"
        :label="t('changePassword.current')"
        autocomplete="current-password"
        :disabled="saving"
        :invalid="errorKey === 'CURRENT_PASSWORD_INCORRECT'"
        required
      />
      <TextField
        id="new-password"
        v-model="next"
        type="password"
        :label="t('changePassword.new')"
        :hint="t('changePassword.hint')"
        autocomplete="new-password"
        :disabled="saving"
        required
      />
      <TextField
        id="confirm-password"
        v-model="confirm"
        type="password"
        :label="t('changePassword.confirm')"
        autocomplete="new-password"
        :disabled="saving"
        :invalid="errorKey === 'mismatch'"
        required
      />
      <SubmitButton
        :loading="saving"
        :loading-text="t('changePassword.saving')"
      >
        {{ t('changePassword.submit') }}
      </SubmitButton>
    </form>

    <div class="support mt-6 text-center">
      <button
        type="button"
        class="link cursor-pointer border-0 bg-transparent p-0 text-xs text-[#616161] underline hover:text-[#242424]"
        @click="signOut"
      >
        {{ t('auth.signOut') }}
      </button>
    </div>
  </AuthLayout>
</template>

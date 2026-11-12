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
      class="title"
      :class="{ tight: forced || errorMessage }"
    >
      <span>{{ t('changePassword.title') }}</span>
      <span
        v-if="t('changePassword.titleSub')"
        class="sub"
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
      class="form"
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

    <div class="support">
      <button
        type="button"
        class="link"
        @click="signOut"
      >
        {{ t('auth.signOut') }}
      </button>
    </div>
  </AuthLayout>
</template>

<style scoped>
.title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 24px;
  font-size: 16px;
  font-weight: 500;
}

.title.tight {
  margin-bottom: 16px;
}

.sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.support {
  margin-top: 24px;
  text-align: center;
}

.link {
  padding: 0;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-decoration: underline;
  cursor: pointer;
}

.link:hover {
  color: var(--color-text);
}
</style>

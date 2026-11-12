<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import * as userApi from '../api'
import type { AssignableRole, ManagedUser } from '../types'

const props = defineProps<{ user: ManagedUser | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t, te } = useI18n()
const editing = computed(() => props.user !== null)
const roleLocked = computed(() => props.user?.systemRole === 'ADMIN')

const email = ref(props.user?.email ?? '')
const password = ref('')
const fullName = ref(props.user?.fullName ?? '')
const position = ref(props.user?.position ?? '')
const role = ref<AssignableRole>(props.user?.systemRole === 'MANAGER' ? 'MANAGER' : 'USER')
const saving = ref(false)
const errorCode = ref('')
const invalidFields = ref<string[]>([])

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function submit() {
  if (saving.value) return
  errorCode.value = ''
  invalidFields.value = []
  saving.value = true
  try {
    const pos = position.value.trim() || null
    if (props.user) {
      const updated = await userApi.updateUser(props.user.id, {
        fullName: fullName.value.trim(),
        position: pos,
        version: props.user.version,
      })
      if (!roleLocked.value && role.value !== updated.systemRole) {
        await userApi.changeUserRole(updated.id, role.value, updated.version)
      }
    } else {
      await userApi.createUser({
        email: email.value.trim(),
        temporaryPassword: password.value,
        fullName: fullName.value.trim(),
        position: pos,
        systemRole: role.value,
      })
    }
    emit('saved')
  } catch (error) {
    const apiError = toApiError(error)
    errorCode.value = apiError.code
    invalidFields.value = apiError.details.map((d) => d.field)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ModalDialog
    :title="editing ? t('users.editTitle') : t('users.createTitle')"
    @close="emit('close')"
  >
    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>
    <form
      class="form"
      @submit.prevent="submit"
    >
      <TextField
        id="user-email"
        v-model="email"
        type="email"
        :label="t('auth.email')"
        :disabled="editing || saving"
        :invalid="invalidFields.includes('email')"
        required
      />
      <TextField
        v-if="!editing"
        id="user-password"
        v-model="password"
        type="password"
        autocomplete="new-password"
        :label="t('users.temporaryPassword')"
        :hint="t('users.temporaryPasswordHint')"
        :disabled="saving"
        :invalid="invalidFields.includes('temporaryPassword')"
        required
      />
      <TextField
        id="user-fullname"
        v-model="fullName"
        :label="t('users.fullName')"
        :disabled="saving"
        :invalid="invalidFields.includes('fullName')"
        required
      />
      <TextField
        id="user-position"
        v-model="position"
        :label="t('users.position')"
        :disabled="saving"
      />
      <div class="field">
        <label
          for="user-role"
          class="label"
        >{{ t('users.role') }}</label>
        <select
          id="user-role"
          v-model="role"
          class="select"
          :disabled="saving || roleLocked"
        >
          <option value="USER">
            {{ t('users.roles.USER') }}
          </option>
          <option value="MANAGER">
            {{ t('users.roles.MANAGER') }}
          </option>
        </select>
        <p
          v-if="roleLocked"
          class="hint"
        >
          {{ t('users.adminRoleFixed') }}
        </p>
      </div>
      <div class="buttons">
        <button
          type="button"
          class="btn secondary"
          :disabled="saving"
          @click="emit('close')"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn primary"
          :disabled="saving"
        >
          {{ saving ? t('changePassword.saving') : t('common.save') }}
        </button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}

.select {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--color-text);
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.btn {
  height: 40px;
  padding: 0 20px;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: #fff;
}

.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.secondary {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}
</style>

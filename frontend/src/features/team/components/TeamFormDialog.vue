<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import * as teamApi from '../api'
import type { TeamDetail } from '../types'
import UserPicker from './UserPicker.vue'

/** Tạo nhóm (chọn Leader, Member, Observer) hoặc sửa thông tin nhóm (tên, mô tả). Thành viên sửa ở trang chi tiết. */
const props = defineProps<{ team: TeamDetail | null }>()
const emit = defineEmits<{ close: []; saved: [team: TeamDetail] }>()

const { t, te } = useI18n()
const editing = computed(() => props.team !== null)

const name = ref(props.team?.name ?? '')
const description = ref(props.team?.description ?? '')
const leaders = ref<number[]>([])
const members = ref<number[]>([])
const observers = ref<number[]>([])
const saving = ref(false)
const errorCode = ref('')
const invalidFields = ref<string[]>([])
const needLeader = ref(false)

const errorMessage = computed(() => {
  if (needLeader.value) return t('team.leaderRequired')
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

// Một người chỉ ở một ô: người đã chọn ở ô này thì ẩn khỏi các ô kia
const takenForLeaders = computed(() => members.value)
const takenForMembers = computed(() => leaders.value)

async function submit() {
  if (saving.value) return
  errorCode.value = ''
  invalidFields.value = []
  needLeader.value = false
  if (!editing.value && leaders.value.length === 0) {
    needLeader.value = true
    return
  }
  saving.value = true
  try {
    const desc = description.value.trim() || null
    const saved = props.team
      ? await teamApi.updateTeam(props.team.id, { name: name.value.trim(), description: desc, version: props.team.version })
      : await teamApi.createTeam({
          name: name.value.trim(),
          description: desc,
          leaderIds: leaders.value,
          memberIds: members.value,
          observerIds: observers.value,
        })
    emit('saved', saved)
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
    :title="editing ? t('team.editTitle') : t('team.createTitle')"
    wide
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
        id="team-name"
        v-model="name"
        :label="t('team.name')"
        :disabled="saving"
        :invalid="invalidFields.includes('name') || errorCode === 'TEAM_NAME_ALREADY_EXISTS'"
        required
      />
      <TextField
        id="team-description"
        v-model="description"
        :label="t('team.description')"
        :disabled="saving"
      />
      <template v-if="!editing">
        <UserPicker
          v-model="leaders"
          :label="t('team.roles.LEADER')"
          role="USER"
          :excluded="takenForLeaders"
        />
        <UserPicker
          v-model="members"
          :label="t('team.roles.MEMBER')"
          role="USER"
          :excluded="takenForMembers"
        />
        <UserPicker
          v-model="observers"
          :label="t('team.roles.OBSERVER')"
          role="MANAGER"
        />
      </template>
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

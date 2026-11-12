<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import type { TeamMemberInfo } from '../../team/types'
import * as taskApi from '../api'
import type { Task, TaskPriority } from '../types'

/** Giao task mới hoặc sửa task. Người phụ trách chọn trong số Member của nhóm. */
const props = defineProps<{ teamId: number; task: Task | null; members: TeamMemberInfo[] }>()
const emit = defineEmits<{ close: []; saved: [task: Task] }>()

const { t, te } = useI18n()
const editing = computed(() => props.task !== null)
const assignable = computed(() => props.members.filter((m) => m.teamRole === 'MEMBER'))

const title = ref(props.task?.title ?? '')
const description = ref(props.task?.description ?? '')
const assigneeId = ref<number | ''>(props.task?.assignee?.id ?? assignable.value[0]?.userId ?? '')
const dueDate = ref(props.task?.dueDate ?? '')
const priority = ref<TaskPriority>(props.task?.priority ?? 'MEDIUM')
const requireFile = ref(props.task?.requireFile ?? false)
const saving = ref(false)
const errorCode = ref('')
const invalidFields = ref<string[]>([])

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function submit() {
  if (saving.value || assigneeId.value === '') return
  errorCode.value = ''
  invalidFields.value = []
  saving.value = true
  try {
    const input = {
      title: title.value.trim(),
      description: description.value.trim() || null,
      assigneeId: assigneeId.value,
      dueDate: dueDate.value || null,
      priority: priority.value,
      requireFile: requireFile.value,
    }
    const saved = props.task
      ? await taskApi.updateTask(props.task.id, input, props.task.version)
      : await taskApi.createTask(props.teamId, input)
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
    :title="editing ? t('task.editTitle') : t('task.createTitle')"
    wide
    @close="emit('close')"
  >
    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>
    <AlertBanner
      v-else-if="assignable.length === 0"
      tone="info"
    >
      {{ t('task.noAssignable') }}
    </AlertBanner>
    <form
      class="form"
      @submit.prevent="submit"
    >
      <TextField
        id="task-title"
        v-model="title"
        :label="t('task.titleLabel')"
        :disabled="saving"
        :invalid="invalidFields.includes('title')"
        required
      />
      <div class="field">
        <label
          for="task-description"
          class="label"
        >{{ t('team.description') }}</label>
        <textarea
          id="task-description"
          v-model="description"
          class="textarea"
          rows="3"
          :disabled="saving"
        />
      </div>
      <div class="field">
        <label
          for="task-assignee"
          class="label"
        >{{ t('task.assignee') }}</label>
        <select
          id="task-assignee"
          v-model="assigneeId"
          class="select"
          :disabled="saving"
          required
        >
          <option
            v-for="m in assignable"
            :key="m.userId"
            :value="m.userId"
          >
            {{ m.fullName }}
          </option>
        </select>
      </div>
      <div class="row">
        <div class="field">
          <label
            for="task-due"
            class="label"
          >{{ t('task.dueDate') }}</label>
          <input
            id="task-due"
            v-model="dueDate"
            type="date"
            class="select"
            :disabled="saving"
          >
        </div>
        <div class="field">
          <label
            for="task-priority"
            class="label"
          >{{ t('task.priority') }}</label>
          <select
            id="task-priority"
            v-model="priority"
            class="select"
            :disabled="saving"
          >
            <option value="LOW">
              {{ t('task.priorities.LOW') }}
            </option>
            <option value="MEDIUM">
              {{ t('task.priorities.MEDIUM') }}
            </option>
            <option value="HIGH">
              {{ t('task.priorities.HIGH') }}
            </option>
          </select>
        </div>
      </div>
      <label class="check">
        <input
          v-model="requireFile"
          type="checkbox"
          :disabled="saving"
        >
        {{ t('task.requireFile') }}
      </label>
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
          :disabled="saving || assignable.length === 0"
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

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}

.select,
.textarea {
  width: 100%;
  padding: 0 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--color-text);
}

.select {
  height: 44px;
}

.textarea {
  padding: 10px 12px;
  resize: vertical;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
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

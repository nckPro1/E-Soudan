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
      class="form flex flex-col gap-4"
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
          class="label field-label"
        >{{ t('team.description') }}</label>
        <textarea
          id="task-description"
          v-model="description"
          class="textarea control w-full resize-y"
          rows="3"
          :disabled="saving"
        />
      </div>
      <div class="field">
        <label
          for="task-assignee"
          class="label field-label"
        >{{ t('task.assignee') }}</label>
        <select
          id="task-assignee"
          v-model="assigneeId"
          class="select control w-full"
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
      <div class="row grid grid-cols-2 gap-4">
        <div class="field">
          <label
            for="task-due"
            class="label field-label"
          >{{ t('task.dueDate') }}</label>
          <input
            id="task-due"
            v-model="dueDate"
            type="date"
            class="select control w-full"
            :disabled="saving"
          >
        </div>
        <div class="field">
          <label
            for="task-priority"
            class="label field-label"
          >{{ t('task.priority') }}</label>
          <select
            id="task-priority"
            v-model="priority"
            class="select control w-full"
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
      <label class="check flex items-center gap-2 text-[0.8125rem]">
        <input
          v-model="requireFile"
          type="checkbox"
          :disabled="saving"
        >
        {{ t('task.requireFile') }}
      </label>
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="saving"
          @click="emit('close')"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="saving || assignable.length === 0"
        >
          {{ saving ? t('changePassword.saving') : t('common.save') }}
        </button>
      </div>
    </form>
  </ModalDialog>
</template>

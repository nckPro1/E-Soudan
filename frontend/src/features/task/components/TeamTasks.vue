<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import type { PageResult } from '../../../shared/types/api'
import type { TeamMemberInfo } from '../../team/types'
import * as taskApi from '../api'
import type { Task, TaskStatus } from '../types'
import TaskFormDialog from './TaskFormDialog.vue'
import TaskTable from './TaskTable.vue'

/** Danh sách task của một nhóm, nằm trong trang chi tiết nhóm. Leader (nhóm đang hoạt động) giao được việc. */
const props = defineProps<{ teamId: number; members: TeamMemberInfo[]; canAssign: boolean }>()

const PAGE_SIZE = 20
const { t, te } = useI18n()
const router = useRouter()

const result = ref<PageResult<Task> | null>(null)
const loading = ref(false)
const errorCode = ref('')
const page = ref(0)
const status = ref<TaskStatus | ''>('')
const assigneeId = ref<number | ''>('')
const creating = ref(false)

const assignees = computed(() => props.members.filter((m) => m.teamRole === 'MEMBER'))
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

let requestSeq = 0

async function load() {
  const seq = ++requestSeq
  loading.value = true
  errorCode.value = ''
  try {
    const data = await taskApi.listTeamTasks(props.teamId, {
      page: page.value,
      size: PAGE_SIZE,
      status: status.value,
      assigneeId: assigneeId.value,
    })
    if (seq === requestSeq) result.value = data
  } catch (error) {
    if (seq === requestSeq) errorCode.value = toApiError(error).code
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch([status, assigneeId], () => {
  page.value = 0
  void load()
})
onMounted(load)

function goTo(target: number) {
  page.value = target
  void load()
}

async function onCreated(task: Task) {
  creating.value = false
  await router.push({ name: 'task-detail', params: { id: task.id } })
}
</script>

<template>
  <section>
    <div class="head mb-3 mt-8 flex items-center justify-between">
      <h2 class="text-base font-bold">
        {{ t('task.title') }}
      </h2>
      <button
        v-if="canAssign"
        type="button"
        class="btn btn-primary"
        @click="creating = true"
      >
        {{ t('task.create') }}
      </button>
    </div>

    <div class="filters mb-3 flex flex-wrap gap-3">
      <select
        v-model="status"
        class="control"
        :aria-label="t('users.status')"
      >
        <option value="">
          {{ t('users.allStatuses') }}
        </option>
        <option
          v-for="s in ['ASSIGNED', 'IN_PROGRESS', 'PENDING_REVIEW', 'DONE']"
          :key="s"
          :value="s"
        >
          {{ t(`task.statuses.${s}`) }}
        </option>
      </select>
      <select
        v-model="assigneeId"
        class="control"
        :aria-label="t('task.assignee')"
      >
        <option value="">
          {{ t('task.allAssignees') }}
        </option>
        <option
          v-for="m in assignees"
          :key="m.userId"
          :value="m.userId"
        >
          {{ m.fullName }}
        </option>
      </select>
    </div>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <TaskTable
      :tasks="result?.items ?? []"
      :loading="loading && !result"
    />

    <div
      v-if="result && result.totalPages > 1"
      class="pager mt-4 flex items-center justify-center gap-4 text-[0.8125rem]"
    >
      <button
        type="button"
        class="btn"
        :disabled="page === 0 || loading"
        @click="goTo(page - 1)"
      >
        {{ t('common.prev') }}
      </button>
      <span>{{ t('common.pageOf', { page: page + 1, total: result.totalPages }) }}</span>
      <button
        type="button"
        class="btn"
        :disabled="page + 1 >= result.totalPages || loading"
        @click="goTo(page + 1)"
      >
        {{ t('common.next') }}
      </button>
    </div>

    <TaskFormDialog
      v-if="creating"
      :team-id="teamId"
      :task="null"
      :members="members"
      @close="creating = false"
      @saved="onCreated"
    />
  </section>
</template>

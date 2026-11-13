<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import type { PageResult } from '../../../shared/types/api'
import * as taskApi from '../api'
import TaskTable from '../components/TaskTable.vue'
import type { Task, TaskStatus } from '../types'

const PAGE_SIZE = 20

const { t, te } = useI18n()
const result = ref<PageResult<Task> | null>(null)
const loading = ref(false)
const errorCode = ref('')
const page = ref(0)
const status = ref<TaskStatus | ''>('')

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
    const data = await taskApi.listMyTasks({ page: page.value, size: PAGE_SIZE, status: status.value })
    if (seq === requestSeq) result.value = data
  } catch (error) {
    if (seq === requestSeq) errorCode.value = toApiError(error).code
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch(status, () => {
  page.value = 0
  void load()
})
onMounted(load)

function goTo(target: number) {
  page.value = target
  void load()
}
</script>

<template>
  <div>
    <div class="head mb-6 flex items-center justify-between">
      <h1 class="page-title">
        {{ t('task.myTasks') }}
      </h1>
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
    </div>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <TaskTable
      :tasks="result?.items ?? []"
      :loading="loading && !result"
      show-team
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
  </div>
</template>

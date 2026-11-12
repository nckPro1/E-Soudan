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
    <div class="head">
      <h1>{{ t('task.myTasks') }}</h1>
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
      class="pager"
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

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

h1 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

.control {
  height: 36px;
  padding: 0 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--color-text);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  font-size: 13px;
}

.btn {
  height: 36px;
  padding: 0 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

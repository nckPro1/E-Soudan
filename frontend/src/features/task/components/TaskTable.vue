<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { Task } from '../types'
import TaskStatusChip from './TaskStatusChip.vue'

/** Bảng task dùng chung cho "Việc của tôi" và danh sách task của nhóm. */
defineProps<{ tasks: Task[]; showTeam?: boolean; loading?: boolean }>()
const { t } = useI18n()

const today = new Date().toISOString().slice(0, 10)
const overdue = (task: Task) => task.dueDate !== null && task.dueDate < today && task.status !== 'DONE'
</script>

<template>
  <div class="table-wrap">
    <table class="table">
      <thead>
        <tr>
          <th>{{ t('task.titleLabel') }}</th>
          <th v-if="showTeam">
            {{ t('team.title') }}
          </th>
          <th>{{ t('task.assignee') }}</th>
          <th>{{ t('task.dueDate') }}</th>
          <th>{{ t('task.priority') }}</th>
          <th>{{ t('users.status') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="task in tasks"
          :key="task.id"
        >
          <td>
            <RouterLink
              :to="{ name: 'task-detail', params: { id: task.id } }"
              class="name"
            >
              {{ task.title }}
            </RouterLink>
          </td>
          <td v-if="showTeam">
            {{ task.teamName ?? '' }}
          </td>
          <td>{{ task.assignee?.fullName ?? '' }}</td>
          <td :class="{ overdue: overdue(task) }">
            {{ task.dueDate ?? '' }}
            <span v-if="overdue(task)">({{ t('task.overdue') }})</span>
          </td>
          <td>{{ t(`task.priorities.${task.priority}`) }}</td>
          <td>
            <TaskStatusChip :status="task.status" />
          </td>
        </tr>
        <tr v-if="!loading && tasks.length === 0">
          <td
            :colspan="showTeam ? 6 : 5"
            class="empty"
          >
            {{ t('task.empty') }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-wrap {
  overflow-x: auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

th {
  background: var(--color-surface-muted);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.name {
  color: var(--color-primary);
  font-weight: 500;
  text-decoration: none;
}

.name:hover {
  text-decoration: underline;
}

.overdue {
  color: var(--color-danger);
}

.empty {
  padding: 32px;
  text-align: center;
  color: var(--color-text-secondary);
}
</style>

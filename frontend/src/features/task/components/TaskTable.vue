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
    <table class="table data-table">
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
              class="name link"
            >
              {{ task.title }}
            </RouterLink>
          </td>
          <td v-if="showTeam">
            {{ task.teamName ?? '' }}
          </td>
          <td>
            {{ task.assignee?.fullName ?? '' }}
            <span
              v-if="task.assignee && task.assignee.active === false"
              class="ml-1 rounded bg-[#c4314b]/10 px-1.5 py-px text-[0.6875rem] font-semibold text-[#c4314b]"
              :title="t('task.assigneeLockedHint')"
              data-testid="assignee-locked"
            >{{ t('task.assigneeLocked') }}</span>
          </td>
          <td :class="{ 'overdue !text-[#c4314b]': overdue(task) }">
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
            class="empty empty-cell"
          >
            {{ t('task.empty') }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

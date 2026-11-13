<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { TaskStatus } from '../types'

defineProps<{ status: TaskStatus }>()
const { t } = useI18n()
</script>

<template>
  <span
    class="chip inline-block whitespace-nowrap rounded border px-2 py-0.5 text-xs"
    :class="[
      status.toLowerCase(),
      {
        ASSIGNED: 'border-[#e1dfdd] text-[#616161]',
        IN_PROGRESS: 'border-[#4f52b2] text-[#4f52b2]',
        PENDING_REVIEW: 'border-[#d9822b] bg-[#d9822b] text-white',
        DONE: 'border-[#107c41] bg-[#107c41] text-white',
      }[status],
    ]"
  >{{ t(`task.statuses.${status}`) }}</span>
</template>

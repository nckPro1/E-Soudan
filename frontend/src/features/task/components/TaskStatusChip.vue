<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { TaskStatus } from '../types'

defineProps<{ status: TaskStatus }>()
const { t } = useI18n()
</script>

<template>
  <span
    class="chip"
    :class="status.toLowerCase()"
  >{{ t(`task.statuses.${status}`) }}</span>
</template>

<style scoped>
.chip {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  white-space: nowrap;
}

.assigned {
  color: var(--color-text-secondary);
}

.in_progress {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* Chờ duyệt: cam */
.pending_review {
  border-color: var(--color-warning);
  background: var(--color-warning);
  color: #fff;
}

/* Hoàn thành: xanh */
.done {
  border-color: var(--color-success);
  background: var(--color-success);
  color: #fff;
}
</style>

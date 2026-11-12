<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Reaction } from '../types'

/**
 * Tóm tắt biểu cảm của một bình luận dạng viên thuốc nhỏ (như Facebook): vài emoji nhiều nhất kèm tổng số.
 * Rê chuột để xem nhanh, bấm để mở danh sách ai đã thả emoji nào.
 */
const props = defineProps<{ reactions: Reaction[] }>()

const { t } = useI18n()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

const total = computed(() => props.reactions.reduce((sum, r) => sum + r.count, 0))
/** Tối đa 3 emoji hiển thị, nhiều người thả nhất đứng trước. */
const top = computed(() => [...props.reactions].sort((a, b) => b.count - a.count).slice(0, 3))
const summary = computed(() => props.reactions.map((r) => `${r.emoji} ${r.users.join(', ')}`).join('\n'))

function onOutside(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}
function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}
onMounted(() => {
  document.addEventListener('mousedown', onOutside)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div
    ref="root"
    class="summary"
  >
    <button
      type="button"
      class="pill"
      :title="summary"
      :aria-label="t('task.whoReacted')"
      :aria-expanded="open"
      data-testid="reaction-summary"
      @click="open = !open"
    >
      <span class="emojis">
        <span
          v-for="reaction in top"
          :key="reaction.emoji"
        >{{ reaction.emoji }}</span>
      </span>
      <span class="total">{{ total }}</span>
    </button>

    <div
      v-if="open"
      class="popover"
      role="dialog"
      :aria-label="t('task.whoReacted')"
    >
      <p
        v-for="reaction in reactions"
        :key="reaction.emoji"
        class="line"
      >
        <span class="emoji">{{ reaction.emoji }}</span>
        <span>{{ reaction.users.join(', ') }}</span>
      </p>
    </div>
  </div>
</template>

<style scoped>
.summary {
  position: relative;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 7px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 11px;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.pill:hover {
  background: var(--color-surface-muted);
}

.emojis {
  display: inline-flex;
  font-size: 13px;
  line-height: 1;
}

.emojis span + span {
  margin-left: -2px;
}

.total {
  font-weight: 500;
}

.popover {
  position: absolute;
  right: 0;
  bottom: 28px;
  z-index: 20;
  min-width: 180px;
  max-width: 260px;
  padding: 8px 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.line {
  display: flex;
  gap: 8px;
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.4;
}

.emoji {
  font-size: 16px;
}
</style>

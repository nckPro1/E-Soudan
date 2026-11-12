<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Reaction } from '../types'
// Component này nhẹ; thư viện emoji bên trong nó chỉ được tải khi bảng chọn đầy đủ thật sự mở (v-if bên dưới).
import EmojiPicker from './EmojiPicker.vue'

/**
 * Nút "Cảm xúc" mở bảng chọn biểu cảm: hàng emoji nhanh và nút "Thêm..." cho bảng đầy đủ.
 * Chọn lại emoji mình đã thả thì là gỡ nó (component cha xử lý bằng sự kiện pick).
 */
const props = defineProps<{ reactions: Reaction[]; disabled?: boolean }>()
const emit = defineEmits<{ pick: [emoji: string] }>()

const QUICK = ['👍', '❤️', '😂', '🎉', '👀', '🙏']

const { t } = useI18n()
const open = ref(false)
const full = ref(false)
const root = ref<HTMLElement | null>(null)

/** Các emoji mình đã thả (để tô sáng trong bảng chọn). */
const mine = computed(() => new Set(props.reactions.filter((r) => r.reactedByMe).map((r) => r.emoji)))
/** Emoji đầu tiên mình đã thả, hiện thay cho chữ "Cảm xúc" như Facebook. */
const myEmoji = computed(() => props.reactions.find((r) => r.reactedByMe)?.emoji ?? null)

function close() {
  open.value = false
  full.value = false
}
function choose(emoji: string) {
  emit('pick', emoji)
  close()
}
function onOutside(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) close()
}
function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') close()
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
  <span
    ref="root"
    class="wrap"
  >
    <button
      type="button"
      class="trigger"
      :class="{ active: myEmoji !== null }"
      :disabled="disabled"
      :aria-expanded="open"
      data-testid="add-reaction"
      @click="open = !open"
    >
      <template v-if="myEmoji">{{ myEmoji }}</template>
      {{ t('task.react') }}
    </button>

    <div
      v-if="open"
      class="popover"
      role="dialog"
      :aria-label="t('task.react')"
    >
      <div class="quick">
        <button
          v-for="emoji in QUICK"
          :key="emoji"
          type="button"
          class="quick-btn"
          :class="{ selected: mine.has(emoji) }"
          :aria-label="emoji"
          :aria-pressed="mine.has(emoji)"
          @click="choose(emoji)"
        >
          {{ emoji }}
        </button>
        <button
          type="button"
          class="more"
          data-testid="more-emoji"
          @click="full = !full"
        >
          {{ t('task.moreEmoji') }}
        </button>
      </div>
      <EmojiPicker
        v-if="full"
        @pick="choose"
      />
    </div>
  </span>
</template>

<style scoped>
.wrap {
  position: relative;
}

.trigger {
  padding: 0;
  background: none;
  border: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.trigger:hover:not(:disabled) {
  text-decoration: underline;
}

.trigger.active {
  color: var(--color-primary);
}

.trigger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.popover {
  position: absolute;
  bottom: 24px;
  left: 0;
  z-index: 20;
  width: 320px;
  max-width: calc(100vw - 48px);
  padding: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.quick-btn {
  width: 36px;
  height: 36px;
  background: none;
  border: 0;
  border-radius: 50%;
  font-size: 22px;
  cursor: pointer;
  transition: transform 0.1s;
}

.quick-btn:hover {
  background: var(--color-surface-muted);
  transform: scale(1.15);
}

.quick-btn.selected {
  background: #e9eef6;
}

.more {
  margin-left: auto;
  padding: 4px 8px;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.more:hover {
  text-decoration: underline;
}

@media (prefers-reduced-motion: reduce) {
  .quick-btn {
    transition: none;
  }
}
</style>

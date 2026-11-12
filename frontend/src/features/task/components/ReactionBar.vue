<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Reaction } from '../types'
// Component này nhẹ; thư viện emoji bên trong nó chỉ được tải khi bảng chọn đầy đủ thật sự mở (v-if bên dưới).
import EmojiPicker from './EmojiPicker.vue'

/** Hàng biểu cảm của một bình luận: các loại đã thả (bấm để thả/gỡ) và nút thêm biểu cảm. */
defineProps<{ reactions: Reaction[]; disabled?: boolean }>()
const emit = defineEmits<{ toggle: [emoji: string] }>()

const QUICK = ['👍', '❤️', '😂', '🎉', '👀', '🙏']

const { t } = useI18n()
const open = ref(false)
const full = ref(false)
const root = ref<HTMLElement | null>(null)

function choose(emoji: string) {
  emit('toggle', emoji)
  open.value = false
  full.value = false
}

function onOutside(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) {
    open.value = false
    full.value = false
  }
}
function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    open.value = false
    full.value = false
  }
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
    class="bar"
  >
    <button
      v-for="reaction in reactions"
      :key="reaction.emoji"
      type="button"
      class="chip"
      :class="{ mine: reaction.reactedByMe }"
      :disabled="disabled"
      :title="reaction.users.join(', ')"
      :aria-pressed="reaction.reactedByMe"
      :aria-label="`${reaction.emoji} ${reaction.count}`"
      @click="emit('toggle', reaction.emoji)"
    >
      <span>{{ reaction.emoji }}</span>
      <span class="count">{{ reaction.count }}</span>
    </button>

    <div class="add-wrap">
      <button
        type="button"
        class="chip add"
        :disabled="disabled"
        :aria-label="t('task.addReaction')"
        :aria-expanded="open"
        data-testid="add-reaction"
        @click="open = !open"
      >
        ☺ +
      </button>
      <div
        v-if="open"
        class="popover"
        role="dialog"
        :aria-label="t('task.addReaction')"
      >
        <div class="quick">
          <button
            v-for="emoji in QUICK"
            :key="emoji"
            type="button"
            class="quick-btn"
            :aria-label="emoji"
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
    </div>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 13px;
  font-size: 13px;
  color: var(--color-text);
  cursor: pointer;
}

.chip:hover:not(:disabled) {
  background: var(--color-surface-muted);
}

.chip:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Mình đã thả: viền và chữ màu chàm */
.chip.mine {
  background: #e9eef6;
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.count {
  font-size: 12px;
  font-weight: 500;
}

.add {
  color: var(--color-text-secondary);
}

.add-wrap {
  position: relative;
}

.popover {
  position: absolute;
  top: 32px;
  left: 0;
  z-index: 20;
  width: 320px;
  max-width: calc(100vw - 48px);
  padding: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.quick-btn {
  width: 34px;
  height: 34px;
  background: none;
  border: 0;
  border-radius: var(--radius);
  font-size: 20px;
  cursor: pointer;
}

.quick-btn:hover {
  background: var(--color-surface-muted);
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
</style>

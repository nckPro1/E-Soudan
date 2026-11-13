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
const props = defineProps<{ reactions: Reaction[]; disabled?: boolean; icon?: boolean; plus?: boolean }>()
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
    class="wrap relative"
  >
    <button
      type="button"
      :class="plus
        ? 'flex items-center pl-1 text-xs text-[#616161] hover:text-black disabled:opacity-60'
        : ['trigger cursor-pointer border-0 bg-transparent p-0 text-xs font-semibold hover:enabled:underline disabled:cursor-not-allowed disabled:opacity-60', myEmoji !== null ? 'active text-[#4f52b2]' : 'text-[#616161]']"
      :disabled="disabled"
      :aria-expanded="open"
      :aria-label="plus ? t('task.react') : undefined"
      :title="plus ? t('task.react') : undefined"
      data-testid="add-reaction"
      @click="open = !open"
    >
      <svg
        v-if="plus"
        viewBox="0 0 24 24"
        class="h-3.5 w-3.5"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <path d="M12 4v16m8-8H4" />
      </svg>
      <svg
        v-else-if="icon && !myEmoji"
        class="ic h-[1.125rem] w-[1.125rem]"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <circle
          cx="12"
          cy="12"
          r="9"
        />
        <path d="M8.5 14.5c.9 1.2 2 1.8 3.5 1.8s2.6-.6 3.5-1.8" />
        <circle
          cx="9"
          cy="10"
          r="0.6"
          fill="currentColor"
        />
        <circle
          cx="15"
          cy="10"
          r="0.6"
          fill="currentColor"
        />
      </svg>
      <template v-if="!plus">
        <template v-if="myEmoji">{{ myEmoji }}</template>
        {{ t('task.react') }}
      </template>
    </button>

    <div
      v-if="open"
      class="popover absolute bottom-6 left-0 z-20 w-80 max-w-[calc(100vw-48px)] rounded-xl bg-white p-2 shadow-xl ring-1 ring-black/10"
      role="dialog"
      :aria-label="t('task.react')"
    >
      <div class="quick flex flex-wrap items-center gap-1">
        <button
          v-for="emoji in QUICK"
          :key="emoji"
          type="button"
          class="quick-btn h-9 w-9 cursor-pointer rounded-full border-0 text-[1.375rem] transition-transform hover:scale-110 hover:bg-[#f5f5f5] motion-reduce:transition-none"
          :class="mine.has(emoji) ? 'selected bg-[#4f52b2]/10' : 'bg-transparent'"
          :aria-label="emoji"
          :aria-pressed="mine.has(emoji)"
          @click="choose(emoji)"
        >
          {{ emoji }}
        </button>
        <button
          type="button"
          class="more ml-auto cursor-pointer border-0 bg-transparent px-2 py-1 text-xs text-[#4f52b2] hover:underline"
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

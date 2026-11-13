<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
// Thư viện emoji nằm trong EmojiPicker và chỉ được tải khi bảng chọn mở (v-if bên dưới).
import EmojiPicker from './EmojiPicker.vue'

/**
 * Nút 😊 mở bảng emoji đầy đủ để chèn emoji vào nội dung đang gõ (bình luận, sửa bình luận).
 * Chỉ phát sự kiện pick; component cha quyết định chèn vào đâu. Không cướp focus khỏi ô nhập khi bấm.
 */
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ pick: [emoji: string] }>()

const { t } = useI18n()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function choose(emoji: string) {
  emit('pick', emoji)
  open.value = false
}
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
  <span
    ref="root"
    class="wrap relative inline-flex"
  >
    <button
      type="button"
      class="trigger flex h-7 w-7 items-center justify-center rounded-full p-0 text-[#616161] hover:bg-[#f5f5f5] hover:text-[#4f52b2] disabled:cursor-not-allowed disabled:opacity-50"
      :disabled="disabled"
      :aria-label="t('task.insertEmoji')"
      :title="t('task.insertEmoji')"
      :aria-expanded="open"
      data-testid="insert-emoji"
      @mousedown.prevent
      @click="open = !open"
    >
      <svg
        viewBox="0 0 24 24"
        class="h-[1.125rem] w-[1.125rem]"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
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
    </button>
    <div
      v-if="open"
      class="popover absolute bottom-9 right-0 z-30 max-w-[calc(100vw-32px)] overflow-hidden rounded-lg bg-white shadow-2xl ring-1 ring-black/10"
      role="dialog"
      :aria-label="t('task.insertEmoji')"
    >
      <EmojiPicker @pick="choose" />
    </div>
  </span>
</template>

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
    class="summary relative"
  >
    <button
      type="button"
      class="pill inline-flex h-[1.375rem] cursor-pointer items-center gap-1 rounded-full border border-[#e1dfdd] bg-white px-[0.4375rem] text-xs text-[#616161] shadow-sm hover:bg-[#f5f5f5]"
      :title="summary"
      :aria-label="t('task.whoReacted')"
      :aria-expanded="open"
      data-testid="reaction-summary"
      @click="open = !open"
    >
      <span class="emojis inline-flex text-[0.8125rem] leading-none">
        <span
          v-for="reaction in top"
          :key="reaction.emoji"
          class="[&+&]:-ml-0.5"
        >{{ reaction.emoji }}</span>
      </span>
      <span class="total font-medium">{{ total }}</span>
    </button>

    <div
      v-if="open"
      class="popover absolute bottom-7 right-0 z-20 min-w-[11.25rem] max-w-[16.25rem] rounded-md bg-white px-3 py-2 shadow-xl ring-1 ring-black/10"
      role="dialog"
      :aria-label="t('task.whoReacted')"
    >
      <p
        v-for="reaction in reactions"
        :key="reaction.emoji"
        class="line my-1 flex gap-2 text-[0.8125rem] leading-snug"
      >
        <span class="emoji text-base">{{ reaction.emoji }}</span>
        <span>{{ reaction.users.join(', ') }}</span>
      </p>
    </div>
  </div>
</template>

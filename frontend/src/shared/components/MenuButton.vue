<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Nút "⋯" mở menu thả xuống gọn (như menu thêm của Teams). Chỉ phát sự kiện select với khoá của mục được chọn.
 * Đóng khi bấm ra ngoài hoặc nhấn Esc.
 */
export interface MenuItem {
  key: string
  label: string
  danger?: boolean
}

defineProps<{ items: MenuItem[]; label: string }>()
const emit = defineEmits<{ select: [key: string] }>()

const open = ref(false)
const root = ref<HTMLElement | null>(null)

function choose(key: string) {
  open.value = false
  emit('select', key)
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
      class="trigger flex h-8 w-8 items-center justify-center rounded-md p-0 text-[#616161] hover:bg-black/5 hover:text-[#242424]"
      :aria-label="label"
      :title="label"
      aria-haspopup="menu"
      :aria-expanded="open"
      data-testid="menu-trigger"
      @click="open = !open"
    >
      <svg
        viewBox="0 0 24 24"
        class="h-[1.125rem] w-[1.125rem]"
        fill="currentColor"
        aria-hidden="true"
      >
        <circle
          cx="5"
          cy="12"
          r="1.8"
        />
        <circle
          cx="12"
          cy="12"
          r="1.8"
        />
        <circle
          cx="19"
          cy="12"
          r="1.8"
        />
      </svg>
    </button>
    <ul
      v-if="open"
      class="menu absolute right-0 top-9 z-40 m-0 min-w-[11.25rem] list-none rounded-md bg-white p-1 shadow-xl ring-1 ring-black/10"
      role="menu"
    >
      <li
        v-for="item in items"
        :key="item.key"
        role="none"
      >
        <button
          type="button"
          role="menuitem"
          class="entry block w-full rounded px-3 py-2 text-left text-sm hover:bg-[#f5f5f5]"
          :class="item.danger ? 'danger text-[#c4314b]' : 'text-[#242424]'"
          @click="choose(item.key)"
        >
          {{ item.label }}
        </button>
      </li>
    </ul>
  </span>
</template>

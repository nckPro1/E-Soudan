<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
// Dữ liệu emoji nhúng sẵn vào bản build (không gọi CDN nào), chỉ tải khi bảng chọn được mở.
import dataSource from 'emoji-picker-element-data/en/emojibase/data.json?url'

/**
 * Bảng chọn emoji đầy đủ (có tìm kiếm, phân nhóm, màu da, "hay dùng") dựa trên emoji-picker-element.
 * Thư viện là web component, nạp trễ để không làm nặng trang khi chưa ai mở bảng chọn.
 */
const emit = defineEmits<{ pick: [emoji: string] }>()
const host = ref<HTMLElement | null>(null)
const failed = ref(false)

let picker: HTMLElement | null = null
const onClick = (event: Event) => {
  const unicode = (event as CustomEvent<{ unicode?: string }>).detail?.unicode
  if (unicode) {
    emit('pick', unicode)
  }
}

onMounted(async () => {
  try {
    await import('emoji-picker-element')
    if (!host.value) return
    picker = document.createElement('emoji-picker')
    picker.setAttribute('data-source', dataSource)
    picker.addEventListener('emoji-click', onClick)
    host.value.appendChild(picker)
  } catch {
    failed.value = true
  }
})

onBeforeUnmount(() => {
  picker?.removeEventListener('emoji-click', onClick)
  picker?.remove()
})
</script>

<template>
  <div
    ref="host"
    class="picker-host [--background:#fff] [--border-color:#e1dfdd] [--border-radius:4px] [--emoji-size:1.3rem] [--indicator-color:#4f52b2] [--input-border-color:#e1dfdd] [--input-border-radius:4px] [--num-columns:8] [--outline-color:#4f52b2] [&_emoji-picker]:h-80 [&_emoji-picker]:w-full"
  >
    <p
      v-if="failed"
      class="fail m-2 text-[#c4314b]"
    >
      …
    </p>
  </div>
</template>

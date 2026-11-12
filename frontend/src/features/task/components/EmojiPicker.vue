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
    class="picker-host"
  >
    <p
      v-if="failed"
      class="fail"
    >
      …
    </p>
  </div>
</template>

<style scoped>
.picker-host {
  --background: var(--color-surface);
  --border-color: var(--color-border);
  --border-radius: 4px;
  --indicator-color: var(--color-primary);
  --input-border-color: var(--color-border);
  --input-border-radius: 4px;
  --outline-color: var(--color-primary);
  --num-columns: 8;
  --emoji-size: 1.3rem;
}

.picker-host :deep(emoji-picker) {
  width: 100%;
  height: 320px;
}

.fail {
  margin: 8px;
  color: var(--color-danger);
}
</style>

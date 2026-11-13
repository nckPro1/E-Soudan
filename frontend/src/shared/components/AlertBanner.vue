<script setup lang="ts">
/**
 * Thông báo trong thẻ, viền trái 3px: đỏ cho lỗi, tím chàm cho thông tin trung tính (hết phiên).
 */
withDefaults(defineProps<{ tone?: 'error' | 'info' }>(), { tone: 'error' })
</script>

<template>
  <div
    class="banner mb-4 rounded border border-l-[3px] border-[#e1dfdd] bg-[#f5f5f5] p-3"
    :class="tone === 'error' ? 'error !border-l-[#c4314b]' : 'info !border-l-[#4f52b2]'"
    role="alert"
  >
    <p
      class="text-xs leading-normal"
      :class="tone === 'error' ? 'font-medium text-[#c4314b]' : 'text-[#242424]'"
    >
      <slot />
    </p>
  </div>
</template>

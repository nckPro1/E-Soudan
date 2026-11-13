<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="brand flex items-center gap-3">
    <!-- Con dấu: ô vuông tím, chữ 報 ("báo cáo") thay cho logo hình ảnh -->
    <span
      class="seal flex h-9 w-9 shrink-0 select-none items-center justify-center rounded bg-[#4f52b2] text-xl font-bold leading-none text-white"
      aria-hidden="true"
    >報</span>
    <span class="name text-lg font-semibold tracking-wide text-[#242424]">{{ t('app.name') }}</span>
  </div>
</template>

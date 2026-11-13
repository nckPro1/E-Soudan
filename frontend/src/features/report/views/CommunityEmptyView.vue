<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/** Khung phải khi chưa chọn cộng đồng nào (màn hình rộng). */
const { t } = useI18n()
</script>

<template>
  <div
    class="empty flex h-full flex-col items-center justify-center p-6 text-center"
    data-testid="pick-community"
  >
    <p class="title mb-1 text-base font-semibold">
      {{ t('report.pickCommunity') }}
    </p>
    <p class="sub max-w-[22.5rem] text-[0.8125rem] text-[#616161]">
      {{ t('report.pickCommunityHint') }}
    </p>
  </div>
</template>

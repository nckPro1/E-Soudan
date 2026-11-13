<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/** Khung phải khi chưa chọn cuộc trò chuyện nào (màn hình rộng). */
const { t } = useI18n()
</script>

<template>
  <div
    class="flex h-full flex-col items-center justify-center p-6 text-center"
    data-testid="pick-chat"
  >
    <p class="mb-1 text-base font-semibold">
      {{ t('chat.pick') }}
    </p>
    <p class="max-w-[24rem] text-[0.8125rem] text-[#616161]">
      {{ t('chat.pickHint') }}
    </p>
    <p class="mt-3 max-w-[24rem] text-xs text-[#616161]">
      {{ t('chat.emptyHint') }}
    </p>
  </div>
</template>

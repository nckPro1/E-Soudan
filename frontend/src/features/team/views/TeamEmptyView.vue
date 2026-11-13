<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/** Khung phải khi chưa chọn nhóm nào (màn hình rộng). */
const { t } = useI18n()
</script>

<template>
  <div
    class="flex h-full flex-col items-center justify-center p-6 text-center"
    data-testid="pick-team"
  >
    <p class="mb-1 text-base font-semibold">
      {{ t('team.pickTeam') }}
    </p>
    <p class="max-w-[24rem] text-[0.8125rem] text-[#616161]">
      {{ t('team.pickTeamHint') }}
    </p>
  </div>
</template>

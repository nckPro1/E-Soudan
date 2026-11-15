<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/** Trang thử CI/CD: không gọi API, không có tính năng. Thấy trang này trên web thật nghĩa là bản mới đã được deploy. */
const { t } = useI18n()
</script>

<template>
  <section
    class="mx-auto max-w-xl p-8 text-center"
    data-testid="cicd-test-page"
  >
    <h1 class="text-xl font-semibold">
      {{ t('cicdTest.title') }}
    </h1>
    <p class="mt-2 text-sm text-gray-600">
      {{ t('cicdTest.body') }}
    </p>
  </section>
</template>

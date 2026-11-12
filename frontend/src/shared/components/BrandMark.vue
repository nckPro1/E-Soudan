<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="brand">
    <!-- Con dấu hanko: ô vuông son, chữ 報 ("báo cáo") thay cho logo hình ảnh -->
    <span
      class="seal"
      aria-hidden="true"
    >報</span>
    <span class="name">{{ t('app.name') }}</span>
  </div>
</template>

<style scoped>
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.seal {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  background: var(--color-danger);
  border: 1px solid var(--color-danger);
  border-radius: 3px;
  color: #fff;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  user-select: none;
}

.name {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--color-text);
}
</style>

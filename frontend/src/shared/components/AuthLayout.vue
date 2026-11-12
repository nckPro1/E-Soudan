<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BrandMark from './BrandMark.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'

const { t } = useI18n()
const year = new Date().getFullYear()
</script>

<template>
  <div class="page">
    <header class="top">
      <LanguageSwitcher />
    </header>

    <main class="center">
      <div class="card">
        <BrandMark class="brand" />
        <slot />
      </div>
    </main>

    <footer class="foot">
      {{ t('footer', { year }) }}
    </footer>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100vh;
}

.top {
  display: flex;
  justify-content: flex-end;
  padding: 24px 32px;
}

.center {
  display: flex;
  flex-grow: 1;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

/* Thẻ phẳng: nền trắng, viền 1px, không đổ bóng (design system, Elevation & Depth) */
.card {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.brand {
  margin-bottom: 32px;
}

.foot {
  padding: 24px 32px;
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
}
</style>

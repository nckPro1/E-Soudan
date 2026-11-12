<script setup lang="ts">
/**
 * Thông báo trong thẻ, viền trái 3px: đỏ son cho lỗi, chàm cho thông tin trung tính (hết phiên).
 */
withDefaults(defineProps<{ tone?: 'error' | 'info' }>(), { tone: 'error' })
</script>

<template>
  <div
    class="banner"
    :class="tone"
    role="alert"
  >
    <p><slot /></p>
  </div>
</template>

<style scoped>
.banner {
  margin-bottom: 24px;
  padding: 12px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-left-width: 3px;
  border-radius: var(--radius-sm);
}

.banner p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.error {
  border-left-color: var(--color-danger);
}

.error p {
  color: var(--color-danger);
  font-weight: 500;
}

.info {
  border-left-color: var(--color-primary);
}

.info p {
  color: var(--color-text);
}
</style>

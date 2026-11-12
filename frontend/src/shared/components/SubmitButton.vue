<script setup lang="ts">
withDefaults(
  defineProps<{
    loading?: boolean
    loadingText?: string
    disabled?: boolean
  }>(),
  { loading: false, loadingText: '', disabled: false },
)
</script>

<template>
  <button
    type="submit"
    class="btn"
    :class="{ loading, off: disabled && !loading }"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <template v-if="loading">
      <svg
        class="spinner"
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <circle
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          stroke-width="3"
          opacity="0.25"
        />
        <path
          fill="currentColor"
          opacity="0.9"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
        />
      </svg>
      <span class="loading-text">{{ loadingText }}</span>
    </template>
    <slot v-else />
  </button>
</template>

<style scoped>
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 44px;
  background: var(--color-primary);
  border: 0;
  border-radius: var(--radius);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s;
}

.btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.btn:active:not(:disabled) {
  background: var(--color-primary-active);
}

.btn.loading {
  gap: 10px;
  opacity: 0.9;
  cursor: not-allowed;
}

.btn.off {
  background: var(--color-disabled-bg);
  color: var(--color-text-secondary);
  cursor: not-allowed;
}

.loading-text {
  letter-spacing: 0.025em;
}

.spinner {
  width: 16px;
  height: 16px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
}
</style>

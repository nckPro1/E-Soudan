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
    class="submit-btn flex h-11 w-full cursor-pointer items-center justify-center rounded-md border-0 bg-[#4f52b2] text-sm font-semibold text-white transition-colors hover:enabled:bg-[#444791] active:enabled:bg-[#3a3d7d]"
    :class="[loading ? 'loading cursor-not-allowed gap-2.5 opacity-90' : '', disabled && !loading ? 'off !cursor-not-allowed !bg-[#e1dfdd] !text-[#616161]' : '']"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <template v-if="loading">
      <svg
        class="spinner h-4 w-4 animate-spin"
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
      <span class="loading-text tracking-wide">{{ loadingText }}</span>
    </template>
    <slot v-else />
  </button>
</template>

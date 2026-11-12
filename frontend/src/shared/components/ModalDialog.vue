<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

defineProps<{ title: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <div
    class="overlay"
    @mousedown.self="emit('close')"
  >
    <section
      class="dialog"
      :class="{ wide }"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
    >
      <h2 class="title">
        {{ title }}
      </h2>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(43, 43, 43, 0.4);
}

.dialog {
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 32px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.dialog.wide {
  max-width: 560px;
}

.title {
  margin: 0 0 24px;
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 500;
}
</style>

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
    class="overlay fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
    @mousedown.self="emit('close')"
  >
    <section
      class="dialog max-h-[90vh] w-full overflow-y-auto rounded-lg bg-white p-6 shadow-2xl ring-1 ring-black/10"
      :class="wide ? 'max-w-[35rem]' : 'max-w-[30rem]'"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
    >
      <h2 class="title mb-5 text-lg font-bold text-[#242424]">
        {{ title }}
      </h2>
      <slot />
    </section>
  </div>
</template>

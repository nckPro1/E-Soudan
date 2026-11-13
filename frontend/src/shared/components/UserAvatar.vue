<script setup lang="ts">
import { computed } from 'vue'
import { colorOf, initialOf } from '../utils/avatar'

/** Ảnh đại diện tròn: chữ cái đại diện trên nền màu cố định theo tên (chưa có ảnh thật). */
const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 36 })

const initial = computed(() => initialOf(props.name))
const background = computed(() => colorOf(props.name))
</script>

<template>
  <span
    class="avatar inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold leading-none text-white"
    :style="{ width: `${size / 16}rem`, height: `${size / 16}rem`, background, fontSize: `${(size * 0.44) / 16}rem` }"
    aria-hidden="true"
  >{{ initial }}</span>
</template>

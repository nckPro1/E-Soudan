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
    class="avatar"
    :style="{ width: `${size}px`, height: `${size}px`, background, fontSize: `${Math.round(size * 0.44)}px` }"
    aria-hidden="true"
  >{{ initial }}</span>
</template>

<style scoped>
.avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 600;
  line-height: 1;
  user-select: none;
}
</style>

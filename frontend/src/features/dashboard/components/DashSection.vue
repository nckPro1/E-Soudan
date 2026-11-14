<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

/** Một khối trên trang tổng quan: tiêu đề, liên kết "Xem tất cả" (tuỳ chọn), nội dung. */
defineProps<{ title: string; to?: RouteLocationRaw; linkLabel?: string }>()
</script>

<template>
  <section class="rounded-xl border border-[#e1dfdd] bg-white shadow-sm">
    <header class="flex items-center justify-between border-b border-[#edebe9] px-4 py-3">
      <h2 class="text-sm font-bold text-[#242424]">
        {{ title }}
      </h2>
      <RouterLink
        v-if="to && linkLabel"
        :to="to"
        class="link text-xs"
      >
        {{ linkLabel }}
      </RouterLink>
    </header>
    <div class="px-4 py-2">
      <slot />
    </div>
  </section>
</template>

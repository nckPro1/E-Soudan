<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

/**
 * Một ô số liệu trên trang tổng quan: nhãn, con số lớn, dòng phụ. Có đường dẫn thì cả ô bấm được.
 * tone: màu nhấn của con số (neutral: tím chàm, danger: đỏ, success: xanh, warning: cam).
 */
withDefaults(
  defineProps<{
    label: string
    value: string | number
    hint?: string
    to?: RouteLocationRaw
    tone?: 'neutral' | 'danger' | 'success' | 'warning'
  }>(),
  { hint: '', to: undefined, tone: 'neutral' },
)

const TONES = {
  neutral: 'text-[#4f52b2]',
  danger: 'text-[#c4314b]',
  success: 'text-[#107c41]',
  warning: 'text-[#ca5010]',
}
</script>

<template>
  <component
    :is="to ? RouterLink : 'div'"
    :to="to"
    class="stat block rounded-xl border border-[#e1dfdd] bg-white p-4 shadow-sm transition"
    :class="to ? 'hover:border-[#4f52b2]/40 hover:shadow-md' : ''"
    data-testid="stat-card"
  >
    <p class="text-xs font-semibold text-[#616161]">
      {{ label }}
    </p>
    <p
      class="mt-1 text-[1.75rem] font-bold leading-tight"
      :class="TONES[tone]"
      data-testid="stat-value"
    >
      {{ value }}
    </p>
    <p
      v-if="hint"
      class="mt-0.5 text-xs text-[#616161]"
    >
      {{ hint }}
    </p>
  </component>
</template>

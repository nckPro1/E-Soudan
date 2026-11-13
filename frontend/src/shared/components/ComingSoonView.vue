<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

/**
 * Trang giữ chỗ cho app chưa làm (Hoạt động, Trò chuyện, Lịch, Trợ lý AI). Nội dung mô tả lấy theo khoá
 * route.meta.module (soon.<module>); kế hoạch chi tiết nằm ở docs/PRODUCT_DIRECTION.md.
 */
const { t, tm } = useI18n()
const route = useRoute()

const key = computed(() => route.meta.module ?? 'activity')
const points = computed(() => (tm(`soon.${key.value}.points`) as string[]) ?? [])
</script>

<template>
  <section
    class="soon mx-auto mt-12 max-w-[35rem] text-center"
    data-testid="coming-soon"
  >
    <span class="badge-soon inline-block rounded-full bg-[#4f52b2]/10 px-2.5 py-0.5 text-xs font-semibold text-[#4f52b2]">{{ t('soon.badge') }}</span>
    <h1 class="mb-2 mt-3 text-[1.375rem] font-semibold">
      {{ t(`soon.${key}.title`) }}
    </h1>
    <p class="lead mb-5 text-sm text-[#616161]">
      {{ t(`soon.${key}.lead`) }}
    </p>
    <ul class="inline-block list-disc pl-5 text-left text-sm leading-8">
      <li
        v-for="(point, index) in points"
        :key="index"
      >
        {{ point }}
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as aiApi from '../api'
import AskPanel from '../components/AskPanel.vue'
import PlanPanel from '../components/PlanPanel.vue'
import SummaryPanel from '../components/SummaryPanel.vue'
import WritePanel from '../components/WritePanel.vue'

/**
 * Trợ lý AI, bố cục hai cột như Teams: cột trái là các công cụ (Hỏi đáp, Tóm tắt báo cáo, Kế hoạch, Viết hộ), khung phải
 * là công cụ đang chọn (nằm trên đường dẫn ?tool=). AI chỉ đọc dữ liệu mình được xem và luôn kèm nguồn để kiểm tra.
 */
type Tool = 'ask' | 'summary' | 'plan' | 'write'
const TOOLS: Tool[] = ['ask', 'summary', 'plan', 'write']
// Đường vẽ icon (viewBox 24, nét 1.8)
const ICONS: Record<Tool, string> = {
  ask: 'M4 5h16v11H9l-5 4z',
  summary: 'M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5M9 13h6M9 17h4',
  plan: 'M5 4h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2zM16 2v4M8 2v4M3 10h18',
  write: 'M15.2 5.2l3.6 3.6M4 20l1-4L16.5 4.5a2.5 2.5 0 013.5 3.5L8.5 19.5z',
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const enabled = ref<boolean | null>(null)
const tool = computed<Tool>(() => {
  const wanted = route.query.tool as Tool | undefined
  return wanted && TOOLS.includes(wanted) ? wanted : 'ask'
})

onMounted(async () => {
  try {
    enabled.value = await aiApi.getStatus()
  } catch {
    enabled.value = null
  }
})

function select(next: Tool) {
  void router.replace({ query: { tool: next } })
}
</script>

<template>
  <div class="flex h-full max-md:flex-col">
    <aside
      class="flex w-[15rem] shrink-0 flex-col border-r border-[#e0e0e0] bg-[#f5f5f5] max-md:w-full max-md:border-b max-md:border-r-0"
      :aria-label="t('nav.ai')"
      data-purpose="chat-sidebar"
    >
      <div class="px-4 pb-2 pt-3">
        <h1 class="text-xl font-bold text-[#242424]">
          {{ t('nav.ai') }}
        </h1>
      </div>
      <nav class="flex flex-col gap-0.5 px-2 pb-3 max-md:flex-row max-md:overflow-x-auto">
        <button
          v-for="item in TOOLS"
          :key="item"
          type="button"
          class="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[0.8125rem] max-md:shrink-0"
          :class="tool === item ? 'bg-white font-semibold shadow-sm ring-1 ring-[#e1dfdd]' : 'text-[#242424] hover:bg-[#edebe9]'"
          :aria-current="tool === item ? 'page' : undefined"
          :data-testid="`tool-${item}`"
          @click="select(item)"
        >
          <svg
            class="h-[1.125rem] w-[1.125rem] shrink-0 text-[#4f52b2]"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path :d="ICONS[item]" /></svg>
          {{ t(`ai.tools.${item}`) }}
        </button>
      </nav>
    </aside>

    <section class="flex min-h-0 min-w-0 flex-1 flex-col bg-white">
      <p
        v-if="enabled === false"
        class="border-b border-[#f1d6a0] bg-[#fff8e5] px-6 py-2 text-xs text-[#7a5b00]"
        data-testid="ai-not-configured"
      >
        {{ t('errors.AI_NOT_CONFIGURED') }}
      </p>
      <div class="min-h-0 flex-1">
        <AskPanel v-if="tool === 'ask'" />
        <SummaryPanel v-else-if="tool === 'summary'" />
        <PlanPanel v-else-if="tool === 'plan'" />
        <WritePanel v-else />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { AiAnswer } from '../types'

/**
 * Câu trả lời của AI: văn bản thuần (Vue tự escape, không dựng HTML từ câu trả lời), các số trích dẫn [n] thành chip bấm
 * được dẫn tới nguồn gốc (báo cáo, công việc, chat nhóm, lịch). Số không có trong danh sách nguồn bị bỏ khỏi chữ.
 * Có nút sao chép cho trường hợp dùng làm nháp.
 */
const props = defineProps<{ answer: AiAnswer; copyable?: boolean }>()

const { t } = useI18n()
const copied = ref(false)

type Part = { kind: 'text'; value: string } | { kind: 'cite'; n: number }

const parts = computed<Part[]>(() => {
  const known = new Set(props.answer.sources.map((s) => s.n))
  const result: Part[] = []
  for (const piece of props.answer.text.split(/(\[\d{1,3}\])/)) {
    const match = /^\[(\d{1,3})\]$/.exec(piece)
    if (!match) {
      if (piece) result.push({ kind: 'text', value: piece })
    } else if (known.has(Number(match[1]))) {
      result.push({ kind: 'cite', n: Number(match[1]) })
    }
  }
  return result
})

const sourceOf = (n: number) => props.answer.sources.find((s) => s.n === n)

async function copy() {
  try {
    await navigator.clipboard.writeText(props.answer.text.replace(/\s*\[\d{1,3}\]/g, ''))
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    // Trình duyệt chặn clipboard: người dùng vẫn bôi đen sao chép tay được
  }
}
</script>

<template>
  <div data-testid="ai-answer">
    <p class="whitespace-pre-wrap break-words text-[0.8125rem] leading-relaxed text-[#242424]">
      <template
        v-for="(part, index) in parts"
        :key="index"
      >
        <span
          v-if="part.kind === 'text'"
          v-text="part.value"
        />
        <component
          :is="sourceOf(part.n)?.path ? RouterLink : 'span'"
          v-else
          :to="sourceOf(part.n)?.path ?? undefined"
          class="mx-0.5 inline-flex h-4 min-w-[1rem] items-center justify-center rounded bg-[#4f52b2]/10 px-1 align-text-top text-[0.625rem] font-bold text-[#4f52b2] no-underline hover:bg-[#4f52b2]/20"
          :title="sourceOf(part.n)?.title"
          data-testid="ai-cite"
        >
          {{ part.n }}
        </component>
      </template>
    </p>

    <ul
      v-if="answer.sources.length > 0"
      class="mt-3 space-y-1 border-t border-[#e1dfdd] pt-2"
      :aria-label="t('ai.sources')"
      data-testid="ai-sources"
    >
      <li
        v-for="source in answer.sources"
        :key="source.n"
        class="flex items-baseline gap-2 text-xs"
      >
        <span class="inline-flex h-4 min-w-[1rem] shrink-0 items-center justify-center rounded bg-[#4f52b2]/10 px-1 text-[0.625rem] font-bold text-[#4f52b2]">{{ source.n }}</span>
        <RouterLink
          v-if="source.path"
          :to="source.path"
          class="link min-w-0 truncate"
          :title="source.title"
        >
          {{ source.title }}
        </RouterLink>
        <span
          v-else
          class="truncate"
        >{{ source.title }}</span>
        <span class="shrink-0 rounded border border-[#e1dfdd] px-1 text-[0.625rem] text-[#616161]">{{ t(`ai.kinds.${source.kind}`) }}</span>
      </li>
    </ul>

    <button
      v-if="copyable"
      type="button"
      class="btn mt-3"
      data-testid="ai-copy"
      @click="copy"
    >
      {{ copied ? t('ai.copied') : t('ai.copy') }}
    </button>
  </div>
</template>

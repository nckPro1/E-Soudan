<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import * as aiApi from '../api'
import type { AiAnswer, AiHistoryTurn } from '../types'
import { useAiCall, useAiLang } from '../useAiCall'
import AiAnswerBody from './AiAnswerBody.vue'

/**
 * Hỏi đáp có trích nguồn (RAG). Mỗi câu hỏi AI tìm trong dữ liệu mình được xem (báo cáo, công việc, chat nhóm, ghi chú lịch)
 * rồi trả lời kèm số nguồn bấm được. Hội thoại chỉ nằm trên màn hình này; gửi kèm vài lượt gần nhất để câu hỏi nối tiếp hiểu ngữ cảnh.
 */
interface Entry {
  question: string
  answer: AiAnswer | null
  error: string
}

const { t } = useI18n()
const lang = useAiLang()
const { loading, errorMessage, run } = useAiCall()

const input = ref('')
const entries = ref<Entry[]>([])
const scroller = ref<HTMLElement | null>(null)

const examples = ['ex1', 'ex2', 'ex3']

async function send(text: string) {
  const question = text.trim()
  if (!question || loading.value) return
  const history: AiHistoryTurn[] = entries.value.flatMap((e) =>
    e.answer ? [{ role: 'user' as const, text: e.question }, { role: 'assistant' as const, text: e.answer.text }] : [],
  )
  input.value = ''
  // Cập nhật theo vị trí (không so sánh đối tượng: danh sách reactive trả về bản bọc proxy, không bằng đối tượng gốc)
  const index = entries.value.length
  entries.value = [...entries.value, { question, answer: null, error: '' }]
  await scrollDown()
  const result = await run(() => aiApi.ask(question, history, lang.value))
  entries.value = entries.value.map((e, i) =>
    i === index ? { ...e, answer: result, error: result ? '' : errorMessage.value } : e,
  )
  await scrollDown()
}

async function scrollDown() {
  await nextTick()
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void send(input.value)
  }
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div
      ref="scroller"
      class="min-h-0 flex-1 overflow-y-auto px-6 py-4 max-md:px-3"
      data-testid="ask-scroller"
    >
      <div
        v-if="entries.length === 0"
        class="mx-auto mt-10 max-w-[32rem] text-center"
      >
        <p class="mb-1 text-base font-semibold">
          {{ t('ai.askTitle') }}
        </p>
        <p class="mb-4 text-[0.8125rem] text-[#616161]">
          {{ t('ai.askHint') }}
        </p>
        <div class="flex flex-wrap justify-center gap-2">
          <button
            v-for="key in examples"
            :key="key"
            type="button"
            class="rounded-full border border-[#d1d1d1] bg-white px-3 py-1 text-xs text-[#424242] hover:bg-[#f5f5f5]"
            @click="send(t(`ai.examples.${key}`))"
          >
            {{ t(`ai.examples.${key}`) }}
          </button>
        </div>
      </div>

      <template
        v-for="(entry, index) in entries"
        :key="index"
      >
        <div class="mt-3 flex justify-end">
          <div class="max-w-[80%] whitespace-pre-wrap break-words rounded-2xl rounded-tr-sm bg-[#e8ebfa] px-3.5 py-2 text-[0.8125rem]">
            {{ entry.question }}
          </div>
        </div>
        <div class="mt-3 flex gap-2.5">
          <span
            class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#4f52b2] text-white"
            aria-hidden="true"
          >✦</span>
          <div class="min-w-0 max-w-[85%] rounded-2xl rounded-tl-sm bg-[#f0f2f5] px-3.5 py-2.5">
            <p
              v-if="!entry.answer && !entry.error"
              class="flex items-center gap-2 text-[0.8125rem] text-[#616161]"
              role="status"
            >
              <span class="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#4f52b2] border-t-transparent" />
              {{ t('ai.thinking') }}
            </p>
            <AiAnswerBody
              v-else-if="entry.answer"
              :answer="entry.answer"
            />
            <p
              v-else
              class="text-[0.8125rem] text-[#c4314b]"
            >
              {{ entry.error }}
            </p>
          </div>
        </div>
      </template>
    </div>

    <AlertBanner
      v-if="entries.length === 0 && errorMessage"
      class="mx-6 mb-2"
    >
      {{ errorMessage }}
    </AlertBanner>

    <form
      class="flex items-end gap-2 border-t border-[#e1dfdd] px-6 py-3 max-md:px-3"
      @submit.prevent="send(input)"
    >
      <textarea
        v-model="input"
        rows="2"
        maxlength="1000"
        class="control min-w-0 flex-1 resize-none"
        :placeholder="t('ai.askPlaceholder')"
        :aria-label="t('ai.askPlaceholder')"
        data-testid="ask-input"
        @keydown="onKey"
      />
      <button
        type="submit"
        class="btn btn-primary"
        :disabled="loading || !input.trim()"
        data-testid="ask-send"
      >
        {{ t('ai.ask') }}
      </button>
      <button
        v-if="entries.length > 0"
        type="button"
        class="btn"
        :disabled="loading"
        @click="entries = []"
      >
        {{ t('ai.newChat') }}
      </button>
    </form>
  </div>
</template>

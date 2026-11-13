<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import * as aiApi from '../api'
import { REWRITE_MODES, type RewriteMode } from '../types'
import { useAiCall, useAiLang } from '../useAiCall'
import AiAnswerBody from './AiAnswerBody.vue'

/**
 * Hỗ trợ viết: (1) soạn nháp báo cáo hằng ngày từ việc mình vừa làm, (2) chỉnh sửa/rút gọn/dịch một đoạn chữ.
 * AI chỉ đưa bản nháp để sao chép; người dùng tự dán vào ô báo cáo, sửa và đăng.
 */
const { t } = useI18n()
const lang = useAiLang()
const draft = useAiCall()
const rewrite = useAiCall()

const text = ref('')
const mode = ref<RewriteMode>('POLISH')

const runDraft = () => draft.run(() => aiApi.draftReport(lang.value))
const runRewrite = () => text.value.trim() && rewrite.run(() => aiApi.rewrite(text.value, mode.value, lang.value))
</script>

<template>
  <div class="h-full overflow-y-auto px-6 py-5 max-md:px-3">
    <h2 class="mb-1 text-base font-bold">
      {{ t('ai.tools.write') }}
    </h2>
    <p class="mb-5 text-[0.8125rem] text-[#616161]">
      {{ t('ai.writeHint') }}
    </p>

    <!-- Nháp báo cáo -->
    <section class="mb-8">
      <h3 class="mb-1 text-sm font-semibold">
        {{ t('ai.draftTitle') }}
      </h3>
      <p class="mb-3 text-xs text-[#616161]">
        {{ t('ai.draftHint') }}
      </p>
      <button
        type="button"
        class="btn btn-primary"
        :disabled="draft.loading.value"
        data-testid="draft-run"
        @click="runDraft"
      >
        {{ t('ai.draftRun') }}
      </button>
      <p
        v-if="draft.loading.value"
        class="mt-3 flex items-center gap-2 text-[0.8125rem] text-[#616161]"
        role="status"
      >
        <span class="h-4 w-4 animate-spin rounded-full border-2 border-[#4f52b2] border-t-transparent" />
        {{ t('ai.thinking') }}
      </p>
      <AlertBanner
        v-else-if="draft.errorMessage.value"
        class="mt-3"
      >
        {{ draft.errorMessage.value }}
      </AlertBanner>
      <div
        v-else-if="draft.answer.value"
        class="mt-3 rounded-xl border border-[#e1dfdd] bg-[#fafafa] p-4"
      >
        <AiAnswerBody
          :answer="draft.answer.value"
          copyable
        />
      </div>
    </section>

    <!-- Viết lại / dịch -->
    <section>
      <h3 class="mb-1 text-sm font-semibold">
        {{ t('ai.rewriteTitle') }}
      </h3>
      <p class="mb-3 text-xs text-[#616161]">
        {{ t('ai.rewriteHint') }}
      </p>
      <textarea
        v-model="text"
        rows="5"
        maxlength="4000"
        class="control mb-2 w-full resize-y"
        :placeholder="t('ai.rewritePlaceholder')"
        :aria-label="t('ai.rewritePlaceholder')"
        data-testid="rewrite-input"
      />
      <div class="flex flex-wrap items-center gap-3">
        <select
          v-model="mode"
          class="control"
          :aria-label="t('ai.rewriteMode')"
          data-testid="rewrite-mode"
        >
          <option
            v-for="m in REWRITE_MODES"
            :key="m"
            :value="m"
          >
            {{ t(`ai.modes.${m}`) }}
          </option>
        </select>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="rewrite.loading.value || !text.trim()"
          data-testid="rewrite-run"
          @click="runRewrite"
        >
          {{ t('ai.rewriteRun') }}
        </button>
      </div>
      <p
        v-if="rewrite.loading.value"
        class="mt-3 flex items-center gap-2 text-[0.8125rem] text-[#616161]"
        role="status"
      >
        <span class="h-4 w-4 animate-spin rounded-full border-2 border-[#4f52b2] border-t-transparent" />
        {{ t('ai.thinking') }}
      </p>
      <AlertBanner
        v-else-if="rewrite.errorMessage.value"
        class="mt-3"
      >
        {{ rewrite.errorMessage.value }}
      </AlertBanner>
      <div
        v-else-if="rewrite.answer.value"
        class="mt-3 rounded-xl border border-[#e1dfdd] bg-[#fafafa] p-4"
      >
        <AiAnswerBody
          :answer="rewrite.answer.value"
          copyable
        />
      </div>
    </section>
  </div>
</template>

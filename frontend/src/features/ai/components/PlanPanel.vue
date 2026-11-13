<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import * as aiApi from '../api'
import { useAiCall, useAiLang } from '../useAiCall'
import AiAnswerBody from './AiAnswerBody.vue'

/** Kế hoạch làm việc cho 3/7/14 ngày tới từ việc đang mở và ghi chú lịch của mình: việc quá hạn, sắp đến hạn, thứ tự làm. */
const { t } = useI18n()
const lang = useAiLang()
const { loading, answer, errorMessage, run } = useAiCall()
const days = ref(7)

const submit = () => run(() => aiApi.plan(days.value, lang.value))
</script>

<template>
  <div class="h-full overflow-y-auto px-6 py-5 max-md:px-3">
    <h2 class="mb-1 text-base font-bold">
      {{ t('ai.tools.plan') }}
    </h2>
    <p class="mb-4 text-[0.8125rem] text-[#616161]">
      {{ t('ai.planHint') }}
    </p>

    <form
      class="mb-4 flex items-end gap-3"
      @submit.prevent="submit"
    >
      <label class="text-xs font-semibold text-[#616161]">
        {{ t('ai.period') }}
        <select
          v-model.number="days"
          class="control mt-1 block"
        >
          <option :value="3">{{ t('ai.nextDays', { count: 3 }) }}</option>
          <option :value="7">{{ t('ai.nextDays', { count: 7 }) }}</option>
          <option :value="14">{{ t('ai.nextDays', { count: 14 }) }}</option>
        </select>
      </label>
      <button
        type="submit"
        class="btn btn-primary"
        :disabled="loading"
        data-testid="plan-run"
      >
        {{ t('ai.makePlan') }}
      </button>
    </form>

    <p
      v-if="loading"
      class="flex items-center gap-2 text-[0.8125rem] text-[#616161]"
      role="status"
    >
      <span class="h-4 w-4 animate-spin rounded-full border-2 border-[#4f52b2] border-t-transparent" />
      {{ t('ai.thinking') }}
    </p>
    <AlertBanner v-else-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>
    <div
      v-else-if="answer"
      class="rounded-xl border border-[#e1dfdd] bg-[#fafafa] p-4"
    >
      <AiAnswerBody :answer="answer" />
    </div>
  </div>
</template>

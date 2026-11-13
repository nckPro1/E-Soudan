<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { useCommunityStore } from '../../report/store'
import * as aiApi from '../api'
import { useAiCall, useAiLang } from '../useAiCall'
import AiAnswerBody from './AiAnswerBody.vue'

/** Tóm tắt báo cáo của một cộng đồng trong 7/14/30 ngày gần nhất: ai làm gì, vướng mắc cần hỗ trợ. */
const { t } = useI18n()
const lang = useAiLang()
const communities = useCommunityStore()
const { loading, answer, errorMessage, run } = useAiCall()

const communityId = ref<number | null>(null)
const days = ref(7)

onMounted(async () => {
  await communities.load()
  communityId.value ??= communities.sorted[0]?.id ?? null
})

const submit = () => communityId.value !== null && run(() => aiApi.summarizeCommunity(communityId.value!, days.value, lang.value))
</script>

<template>
  <div class="h-full overflow-y-auto px-6 py-5 max-md:px-3">
    <h2 class="mb-1 text-base font-bold">
      {{ t('ai.tools.summary') }}
    </h2>
    <p class="mb-4 text-[0.8125rem] text-[#616161]">
      {{ t('ai.summaryHint') }}
    </p>

    <p
      v-if="communities.sorted.length === 0"
      class="text-[0.8125rem] text-[#616161]"
    >
      {{ t('ai.noCommunity') }}
    </p>

    <form
      v-else
      class="mb-4 flex flex-wrap items-end gap-3"
      @submit.prevent="submit"
    >
      <label class="text-xs font-semibold text-[#616161]">
        {{ t('ai.community') }}
        <select
          v-model="communityId"
          class="control mt-1 block min-w-[14rem]"
          data-testid="summary-community"
        >
          <option
            v-for="c in communities.sorted"
            :key="c.id"
            :value="c.id"
          >{{ c.name }}</option>
        </select>
      </label>
      <label class="text-xs font-semibold text-[#616161]">
        {{ t('ai.period') }}
        <select
          v-model.number="days"
          class="control mt-1 block"
        >
          <option :value="7">{{ t('ai.lastDays', { count: 7 }) }}</option>
          <option :value="14">{{ t('ai.lastDays', { count: 14 }) }}</option>
          <option :value="30">{{ t('ai.lastDays', { count: 30 }) }}</option>
        </select>
      </label>
      <button
        type="submit"
        class="btn btn-primary"
        :disabled="loading || communityId === null"
        data-testid="summary-run"
      >
        {{ t('ai.summarize') }}
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

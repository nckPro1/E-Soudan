<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import type { AiAnswer } from '../types'
import { useAiCall } from '../useAiCall'
import AiAnswerBody from './AiAnswerBody.vue'

/**
 * Hộp thoại tóm tắt chạy ngay khi mở (tóm tắt báo cáo cộng đồng, tóm tắt cuộc trò chuyện). Hiện trạng thái đang xử lý,
 * kết quả kèm nguồn, hoặc lỗi dịch sẵn; có nút "Tóm tắt lại". Kết quả chỉ là gợi ý để đọc nhanh, không ghi gì vào hệ thống.
 */
const props = defineProps<{ title: string; call: () => Promise<AiAnswer> }>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const { loading, answer, errorMessage, run } = useAiCall()

const start = () => run(props.call)
onMounted(start)
</script>

<template>
  <ModalDialog
    :title="title"
    wide
    @close="emit('close')"
  >
    <p class="mb-3 text-xs text-[#616161]">
      {{ t('ai.disclaimer') }}
    </p>

    <div
      v-if="loading"
      class="flex items-center gap-3 py-8 text-[0.8125rem] text-[#616161]"
      role="status"
      data-testid="ai-loading"
    >
      <span class="h-4 w-4 animate-spin rounded-full border-2 border-[#4f52b2] border-t-transparent" />
      {{ t('ai.thinking') }}
    </div>

    <AlertBanner v-else-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <AiAnswerBody
      v-else-if="answer"
      :answer="answer"
    />

    <div class="mt-5 flex justify-end gap-2">
      <button
        type="button"
        class="btn"
        :disabled="loading"
        data-testid="ai-retry"
        @click="start"
      >
        {{ t('ai.retry') }}
      </button>
      <button
        type="button"
        class="btn btn-primary"
        @click="emit('close')"
      >
        {{ t('ai.close') }}
      </button>
    </div>
  </ModalDialog>
</template>

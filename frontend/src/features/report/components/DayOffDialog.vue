<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { toApiError } from '../../../shared/api/http'
import * as reportApi from '../api'
import type { DayOff } from '../types'

/**
 * Báo nghỉ của chính mình: chọn ngày (từ hôm nay trở đi) và lý do. Ngày nghỉ không bị tính là "chưa nộp báo cáo" và không
 * nhận nhắc nộp. Liệt kê các ngày nghỉ sắp tới để bỏ báo nghỉ khi đổi kế hoạch.
 */
const emit = defineEmits<{ close: [] }>()

const { t, te, locale } = useI18n()

const pad = (n: number) => String(n).padStart(2, '0')
const keyOf = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const today = keyOf(new Date())

const days = ref<DayOff[]>([])
const day = ref(today)
const reason = ref('')
const busy = ref(false)
const errorCode = ref('')

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

const label = (key: string) =>
  new Date(`${key}T00:00:00`).toLocaleDateString(locale.value, { weekday: 'short', day: 'numeric', month: 'numeric', year: 'numeric' })

async function load() {
  const end = new Date()
  end.setDate(end.getDate() + 120)
  try {
    days.value = await reportApi.listDaysOff(today, keyOf(end))
  } catch (error) {
    errorCode.value = toApiError(error).code
  }
}
onMounted(load)

async function run(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  errorCode.value = ''
  try {
    await action()
    await load()
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    busy.value = false
  }
}

const add = () =>
  run(async () => {
    await reportApi.markDayOff(day.value, reason.value.trim() || null)
    reason.value = ''
  })
const remove = (key: string) => run(() => reportApi.unmarkDayOff(key))
</script>

<template>
  <ModalDialog
    :title="t('report.dayOff.title')"
    @close="emit('close')"
  >
    <p class="mb-3 text-xs text-[#616161]">
      {{ t('report.dayOff.hint') }}
    </p>
    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <form
      class="mb-4 flex flex-wrap items-end gap-2"
      @submit.prevent="add"
    >
      <label class="text-xs font-semibold text-[#616161]">
        {{ t('report.dayOff.date') }}
        <input
          v-model="day"
          type="date"
          :min="today"
          class="control mt-1 block"
          data-testid="dayoff-date"
        >
      </label>
      <label class="min-w-[10rem] flex-1 text-xs font-semibold text-[#616161]">
        {{ t('report.dayOff.reason') }}
        <input
          v-model="reason"
          type="text"
          maxlength="200"
          class="control mt-1 block w-full"
          :placeholder="t('report.dayOff.reasonPlaceholder')"
          data-testid="dayoff-reason"
        >
      </label>
      <button
        type="submit"
        class="btn btn-primary"
        :disabled="busy || !day"
        data-testid="dayoff-add"
      >
        {{ t('report.dayOff.add') }}
      </button>
    </form>

    <h3 class="mb-1 text-sm font-semibold">
      {{ t('report.dayOff.upcoming') }}
    </h3>
    <p
      v-if="days.length === 0"
      class="text-xs text-[#616161]"
      data-testid="dayoff-empty"
    >
      {{ t('report.dayOff.none') }}
    </p>
    <ul
      v-else
      class="divide-y divide-[#edebe9]"
    >
      <li
        v-for="d in days"
        :key="d.day"
        class="flex items-center justify-between gap-3 py-1.5 text-[0.8125rem]"
        data-testid="dayoff-item"
      >
        <span class="min-w-0">
          <strong class="font-semibold">{{ label(d.day) }}</strong>
          <span
            v-if="d.reason"
            class="ml-2 text-xs text-[#616161]"
          >{{ d.reason }}</span>
        </span>
        <button
          type="button"
          class="btn-link !text-[#c4314b]"
          :disabled="busy"
          :aria-label="`${t('common.delete')}: ${label(d.day)}`"
          @click="remove(d.day)"
        >
          {{ t('common.delete') }}
        </button>
      </li>
    </ul>

    <div class="mt-5 flex justify-end">
      <button
        type="button"
        class="btn"
        @click="emit('close')"
      >
        {{ t('ai.close') }}
      </button>
    </div>
  </ModalDialog>
</template>

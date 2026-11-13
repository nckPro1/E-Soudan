<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import * as calendarApi from '../api'
import DayNotesPanel from '../components/DayNotesPanel.vue'
import { fromKey, monthGrid, shiftMonth, todayKey } from '../dates'
import { COLOR_HEX, type CalendarNote } from '../types'

/**
 * Lịch cá nhân, kiểu Teams: lưới tháng (tuần bắt đầu Thứ Hai) với các ghi chú đánh dấu bằng màu, nút Hôm nay và chuyển
 * tháng; bấm một ngày để xem và thêm ghi chú ở cột bên phải. Ai cũng có lịch riêng, không ai xem được lịch của nhau.
 */
const MAX_CHIPS = 3

const { t, te, locale } = useI18n()

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth())
const selected = ref(todayKey())
const notes = ref<CalendarNote[]>([])
const errorCode = ref('')

const cells = computed(() => monthGrid(year.value, month.value))
const today = todayKey()
const monthLabel = computed(() =>
  new Date(year.value, month.value, 1).toLocaleDateString(locale.value, { month: 'long', year: 'numeric' }),
)
// Tên thứ trong tuần theo ngôn ngữ, bắt đầu từ Thứ Hai (2026-01-05 là Thứ Hai)
const weekdays = computed(() =>
  Array.from({ length: 7 }, (_, i) =>
    new Date(2026, 0, 5 + i).toLocaleDateString(locale.value, { weekday: 'short' }),
  ),
)
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

const byDay = computed(() => {
  const map: Record<string, CalendarNote[]> = {}
  for (const n of notes.value) (map[n.date] ??= []).push(n)
  return map
})
const selectedNotes = computed(() => byDay.value[selected.value] ?? [])

let seq = 0
async function load() {
  const mine = ++seq
  const grid = cells.value
  try {
    const list = await calendarApi.listNotes(grid[0].key, grid[grid.length - 1].key)
    if (mine === seq) {
      notes.value = list
      errorCode.value = ''
    }
  } catch (error) {
    if (mine === seq) errorCode.value = toApiError(error).code
  }
}

onMounted(load)
watch([year, month], () => void load())

function go(delta: number) {
  const next = shiftMonth(year.value, month.value, delta)
  year.value = next.year
  month.value = next.month
}

function goToday() {
  const d = new Date()
  year.value = d.getFullYear()
  month.value = d.getMonth()
  selected.value = todayKey()
}

function pick(key: string) {
  selected.value = key
  const d = fromKey(key)
  // Bấm vào ô của tháng kế bên thì chuyển sang tháng đó
  if (d.getFullYear() !== year.value || d.getMonth() !== month.value) {
    year.value = d.getFullYear()
    month.value = d.getMonth()
  }
}

function onSaved(note: CalendarNote) {
  const exists = notes.value.some((n) => n.id === note.id)
  notes.value = (exists ? notes.value.map((n) => (n.id === note.id ? note : n)) : [...notes.value, note]).sort(
    (a, b) => a.date.localeCompare(b.date) || a.id - b.id,
  )
}

function onRemoved(id: number) {
  notes.value = notes.value.filter((n) => n.id !== id)
}
</script>

<template>
  <div class="flex h-full max-md:flex-col max-md:overflow-y-auto">
    <section
      class="flex min-h-0 min-w-0 flex-1 flex-col"
      data-testid="calendar"
    >
      <header class="flex flex-wrap items-center gap-2 border-b border-[#e1dfdd] px-5 py-3">
        <h1 class="mr-2 text-xl font-bold capitalize text-[#242424]">
          {{ monthLabel }}
        </h1>
        <button
          type="button"
          class="btn"
          data-testid="cal-today"
          @click="goToday"
        >
          {{ t('calendar.today') }}
        </button>
        <button
          type="button"
          class="rounded p-1.5 text-[#616161] hover:bg-black/5"
          :aria-label="t('calendar.prevMonth')"
          data-testid="cal-prev"
          @click="go(-1)"
        >
          <svg
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path
            d="M15 19l-7-7 7-7"
            stroke-linecap="round"
            stroke-linejoin="round"
          /></svg>
        </button>
        <button
          type="button"
          class="rounded p-1.5 text-[#616161] hover:bg-black/5"
          :aria-label="t('calendar.nextMonth')"
          data-testid="cal-next"
          @click="go(1)"
        >
          <svg
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path
            d="M9 5l7 7-7 7"
            stroke-linecap="round"
            stroke-linejoin="round"
          /></svg>
        </button>
      </header>

      <AlertBanner
        v-if="errorMessage"
        class="m-3"
      >
        {{ errorMessage }}
      </AlertBanner>

      <div class="grid grid-cols-7 border-b border-[#e1dfdd] bg-[#fafafa] text-center text-xs font-semibold text-[#616161]">
        <div
          v-for="name in weekdays"
          :key="name"
          class="py-2"
        >
          {{ name }}
        </div>
      </div>

      <div
        class="grid min-h-0 flex-1 grid-cols-7 overflow-y-auto"
        :style="{ gridTemplateRows: `repeat(${cells.length / 7}, minmax(5.5rem, 1fr))` }"
        role="grid"
        :aria-label="monthLabel"
      >
        <button
          v-for="cell in cells"
          :key="cell.key"
          type="button"
          role="gridcell"
          class="flex min-w-0 flex-col items-stretch gap-0.5 border-b border-r border-[#edebe9] p-1 text-left outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#4f52b2]"
          :class="[
            cell.key === selected ? 'bg-[#4f52b2]/10' : cell.inMonth ? 'bg-white hover:bg-[#f5f5f5]' : 'bg-[#fafafa] hover:bg-[#f5f5f5]',
          ]"
          :aria-selected="cell.key === selected"
          :aria-label="`${cell.key}, ${t('calendar.noteCount', { count: (byDay[cell.key] ?? []).length })}`"
          data-testid="day-cell"
          :data-date="cell.key"
          @click="pick(cell.key)"
        >
          <span
            class="flex h-6 w-6 items-center justify-center self-start rounded-full text-xs"
            :class="
              cell.key === today
                ? 'bg-[#4f52b2] font-bold text-white'
                : cell.inMonth
                  ? 'font-semibold text-[#242424]'
                  : 'text-[#a19f9d]'
            "
          >{{ cell.day }}</span>
          <span
            v-for="n in (byDay[cell.key] ?? []).slice(0, MAX_CHIPS)"
            :key="n.id"
            class="truncate rounded px-1.5 py-px text-[0.6875rem] font-medium text-white"
            :style="{ background: COLOR_HEX[n.color] }"
            data-testid="note-chip"
          >{{ n.title }}</span>
          <span
            v-if="(byDay[cell.key] ?? []).length > MAX_CHIPS"
            class="px-1 text-[0.6875rem] text-[#616161]"
          >+{{ (byDay[cell.key] ?? []).length - MAX_CHIPS }}</span>
        </button>
      </div>
    </section>

    <div class="w-[20rem] shrink-0 max-md:h-[24rem] max-md:w-full">
      <DayNotesPanel
        :key="selected"
        :date-key="selected"
        :notes="selectedNotes"
        @saved="onSaved"
        @removed="onRemoved"
      />
    </div>
  </div>
</template>

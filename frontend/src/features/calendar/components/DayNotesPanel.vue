<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import * as calendarApi from '../api'
import { fromKey } from '../dates'
import {
  COLOR_HEX,
  MAX_CONTENT,
  MAX_TITLE,
  NOTE_COLORS,
  type CalendarNote,
  type NoteColor,
} from '../types'

/**
 * Bảng ghi chú của một ngày (cột phải của lịch): danh sách ghi chú, thêm mới, sửa, xoá. Ghi chú là của riêng mình.
 * Form dùng chung cho thêm và sửa; chọn màu đánh dấu từ 6 màu cố định.
 */
const props = defineProps<{ dateKey: string; notes: CalendarNote[] }>()
const emit = defineEmits<{ saved: [note: CalendarNote]; removed: [id: number] }>()

const { t, te, locale } = useI18n()

const editingId = ref<number | 'new' | null>(null)
const title = ref('')
const content = ref('')
const color = ref<NoteColor>('INDIGO')
const busy = ref(false)
const errorCode = ref('')
const titleInput = ref<HTMLInputElement | null>(null)

const heading = computed(() =>
  fromKey(props.dateKey).toLocaleDateString(locale.value, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }),
)
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

// Đổi sang ngày khác thì bỏ form đang mở
watch(
  () => props.dateKey,
  () => cancel(),
)

async function openForm(note: CalendarNote | null) {
  errorCode.value = ''
  editingId.value = note ? note.id : 'new'
  title.value = note?.title ?? ''
  content.value = note?.content ?? ''
  color.value = note?.color ?? 'INDIGO'
  await nextTick()
  titleInput.value?.focus()
}

function cancel() {
  editingId.value = null
  errorCode.value = ''
}

async function save() {
  if (busy.value || !title.value.trim()) return
  busy.value = true
  errorCode.value = ''
  const input = {
    date: props.dateKey,
    title: title.value.trim(),
    content: content.value.trim() || null,
    color: color.value,
  }
  try {
    const saved =
      editingId.value === 'new'
        ? await calendarApi.createNote(input)
        : await calendarApi.updateNote(editingId.value as number, input)
    emit('saved', saved)
    editingId.value = null
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    busy.value = false
  }
}

async function remove(note: CalendarNote) {
  if (busy.value) return
  busy.value = true
  errorCode.value = ''
  try {
    await calendarApi.deleteNote(note.id)
    emit('removed', note.id)
    if (editingId.value === note.id) editingId.value = null
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <aside
    class="flex h-full min-h-0 flex-col border-l border-[#e1dfdd] bg-[#f5f5f5] max-md:border-l-0 max-md:border-t"
    :aria-label="heading"
    data-testid="day-panel"
  >
    <div class="flex items-start justify-between gap-2 px-4 pb-2 pt-4">
      <div class="min-w-0">
        <h2 class="text-base font-bold capitalize text-[#242424]">
          {{ heading }}
        </h2>
        <p class="text-xs text-[#616161]">
          {{ t('calendar.noteCount', { count: notes.length }) }}
        </p>
      </div>
      <button
        v-if="editingId === null"
        type="button"
        class="btn btn-primary shrink-0"
        data-testid="add-note"
        @click="openForm(null)"
      >
        {{ t('calendar.addNote') }}
      </button>
    </div>

    <AlertBanner
      v-if="errorMessage"
      class="mx-4 mb-2"
    >
      {{ errorMessage }}
    </AlertBanner>

    <div class="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
      <!-- Form thêm / sửa -->
      <form
        v-if="editingId !== null"
        class="mb-3 rounded-lg border border-[#e1dfdd] bg-white p-3"
        data-testid="note-form"
        @submit.prevent="save"
      >
        <input
          ref="titleInput"
          v-model="title"
          type="text"
          class="control mb-2 w-full"
          :maxlength="MAX_TITLE"
          :placeholder="t('calendar.titlePlaceholder')"
          :aria-label="t('calendar.titlePlaceholder')"
          data-testid="note-title"
        >
        <textarea
          v-model="content"
          rows="3"
          class="control mb-2 w-full resize-y"
          :maxlength="MAX_CONTENT"
          :placeholder="t('calendar.contentPlaceholder')"
          :aria-label="t('calendar.contentPlaceholder')"
          data-testid="note-content"
        />
        <div
          class="mb-3 flex items-center gap-2"
          role="radiogroup"
          :aria-label="t('calendar.color')"
        >
          <button
            v-for="c in NOTE_COLORS"
            :key="c"
            type="button"
            role="radio"
            class="h-5 w-5 rounded-full ring-offset-2 transition"
            :class="color === c ? 'ring-2 ring-[#242424]' : 'hover:scale-110'"
            :style="{ background: COLOR_HEX[c] }"
            :aria-checked="color === c"
            :aria-label="t(`calendar.colors.${c}`)"
            :title="t(`calendar.colors.${c}`)"
            @click="color = c"
          />
        </div>
        <div class="flex justify-end gap-2">
          <button
            type="button"
            class="btn"
            :disabled="busy"
            @click="cancel"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="busy || !title.trim()"
            data-testid="note-save"
          >
            {{ t('common.save') }}
          </button>
        </div>
      </form>

      <p
        v-if="notes.length === 0 && editingId === null"
        class="py-8 text-center text-[0.8125rem] text-[#616161]"
        data-testid="day-empty"
      >
        {{ t('calendar.dayEmpty') }}
      </p>

      <ul class="space-y-2">
        <li
          v-for="note in notes"
          :key="note.id"
          class="group rounded-lg border border-[#e1dfdd] bg-white p-3"
          :style="{ borderLeft: `4px solid ${COLOR_HEX[note.color]}` }"
          data-testid="note-item"
        >
          <div class="flex items-start justify-between gap-2">
            <p class="min-w-0 break-words text-[0.8125rem] font-semibold">
              {{ note.title }}
            </p>
            <div class="flex shrink-0 gap-1 text-xs">
              <button
                type="button"
                class="btn-link"
                :aria-label="`${t('common.edit')}: ${note.title}`"
                data-testid="note-edit"
                @click="openForm(note)"
              >
                {{ t('common.edit') }}
              </button>
              <button
                type="button"
                class="btn-link !text-[#c4314b]"
                :disabled="busy"
                :aria-label="`${t('common.delete')}: ${note.title}`"
                data-testid="note-delete"
                @click="remove(note)"
              >
                {{ t('common.delete') }}
              </button>
            </div>
          </div>
          <!-- Văn bản thuần: {{ }} luôn được Vue escape -->
          <p
            v-if="note.content"
            class="mt-1 whitespace-pre-wrap break-words text-xs text-[#424242]"
          >
            {{ note.content }}
          </p>
        </li>
      </ul>
    </div>
  </aside>
</template>

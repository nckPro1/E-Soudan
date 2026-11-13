<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { insertAtCaret } from '../../../shared/utils/caret'
import { formatBytes } from '../../../shared/utils/fileSize'
import EmojiInsertButton from '../../task/components/EmojiInsertButton.vue'
import type { ChatMessage } from '../types'

/**
 * Ô soạn tin: Enter gửi, Shift+Enter xuống dòng (đang gõ IME thì Enter chỉ chốt chữ). Có đính kèm file/ảnh, chèn emoji,
 * và thanh "Đang trả lời ..." khi trả lời một tin.
 */
const MAX_BODY = 4000
const MAX_FILES = 10

const props = defineProps<{ busy: boolean; replyTo: ChatMessage | null }>()
const emit = defineEmits<{ send: [payload: { body: string; files: File[] }]; cancelReply: [] }>()

const { t } = useI18n()
const body = ref('')
const files = ref<File[]>([])
const input = ref<HTMLTextAreaElement | null>(null)
const picker = ref<HTMLInputElement | null>(null)
const imagePicker = ref<HTMLInputElement | null>(null)

const canSend = () => !props.busy && (body.value.trim().length > 0 || files.value.length > 0)

function grow() {
  const el = input.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

watch(body, () => nextTick(grow))
watch(
  () => props.replyTo,
  (value) => {
    if (value) input.value?.focus()
  },
)
onMounted(grow)

function insertEmoji(emoji: string) {
  const el = input.value
  const result = insertAtCaret(body.value, el?.selectionStart ?? null, el?.selectionEnd ?? null, emoji, MAX_BODY)
  body.value = result.value
  void nextTick(() => {
    el?.focus()
    el?.setSelectionRange(result.caret, result.caret)
  })
}

function addFiles(event: Event) {
  const target = event.target as HTMLInputElement
  const picked = Array.from(target.files ?? [])
  files.value = [...files.value, ...picked].slice(0, MAX_FILES)
  target.value = ''
}

function removeFile(index: number) {
  files.value = files.value.filter((_, i) => i !== index)
}

function submit() {
  if (!canSend()) return
  emit('send', { body: body.value.trim(), files: files.value })
}

/** Gọi sau khi gửi thành công để xoá nội dung đã soạn. */
function clear() {
  body.value = ''
  files.value = []
  void nextTick(() => input.value?.focus())
}
defineExpose({ clear, focus: () => input.value?.focus() })

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  } else if (event.key === 'Escape' && props.replyTo) {
    emit('cancelReply')
  }
}
</script>

<template>
  <div class="chat-composer border-t border-[#e1dfdd] bg-white px-6 py-3 max-md:px-3">
    <!-- Đang trả lời -->
    <div
      v-if="replyTo"
      class="mb-2 flex items-center gap-2 rounded-lg border-l-2 border-[#4f52b2] bg-[#f5f5f5] px-3 py-1.5 text-xs"
      data-testid="reply-bar"
    >
      <div class="min-w-0 flex-1">
        <span class="font-semibold">{{ t('chat.replyingTo', { name: replyTo.author?.fullName ?? '' }) }}</span>
        <span class="block truncate text-[#616161]">{{ replyTo.body ?? t('chat.attachment') }}</span>
      </div>
      <button
        type="button"
        class="shrink-0 rounded p-1 text-[#616161] hover:bg-[#e1dfdd]"
        :aria-label="t('common.cancel')"
        @click="emit('cancelReply')"
      >
        ✕
      </button>
    </div>

    <!-- File đã chọn -->
    <ul
      v-if="files.length > 0"
      class="mb-2 flex flex-wrap gap-2"
    >
      <li
        v-for="(file, index) in files"
        :key="`${file.name}-${index}`"
        class="flex max-w-[14rem] items-center gap-1.5 rounded-lg border border-[#e1dfdd] bg-[#f5f5f5] px-2 py-1 text-xs"
        data-testid="chat-file-chip"
      >
        <span class="truncate">{{ file.name }}</span>
        <span class="shrink-0 text-[#616161]">{{ formatBytes(file.size) }}</span>
        <button
          type="button"
          class="shrink-0 rounded text-[#616161] hover:text-[#c4314b]"
          :aria-label="t('common.delete')"
          @click="removeFile(index)"
        >
          ✕
        </button>
      </li>
    </ul>

    <form
      class="flex items-end gap-2 rounded-2xl border border-[#e1dfdd] bg-white px-2 py-1.5 focus-within:border-[#4f52b2]"
      @submit.prevent="submit"
    >
      <button
        type="button"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[#616161] hover:bg-[#f5f5f5]"
        :title="t('chat.attachFile')"
        :aria-label="t('chat.attachFile')"
        :disabled="busy || files.length >= MAX_FILES"
        data-testid="chat-attach"
        @click="picker?.click()"
      >
        <svg
          class="h-[1.125rem] w-[1.125rem]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        ><path d="M21 12.5l-8.5 8.5a5.5 5.5 0 01-7.8-7.8l9-9a3.7 3.7 0 015.2 5.2l-9 9a1.8 1.8 0 01-2.6-2.6l8.3-8.3" /></svg>
      </button>
      <button
        type="button"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[#616161] hover:bg-[#f5f5f5]"
        :title="t('chat.attachImage')"
        :aria-label="t('chat.attachImage')"
        :disabled="busy || files.length >= MAX_FILES"
        @click="imagePicker?.click()"
      >
        <svg
          class="h-[1.125rem] w-[1.125rem]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        ><rect
          x="3"
          y="4"
          width="18"
          height="16"
          rx="2"
        /><circle
          cx="9"
          cy="10"
          r="1.5"
        /><path d="M21 16l-5-5-8 8" /></svg>
      </button>
      <input
        ref="picker"
        type="file"
        multiple
        class="hidden"
        data-testid="chat-file-input"
        @change="addFiles"
      >
      <input
        ref="imagePicker"
        type="file"
        multiple
        accept="image/*"
        class="hidden"
        @change="addFiles"
      >

      <textarea
        ref="input"
        v-model="body"
        rows="1"
        :maxlength="MAX_BODY"
        class="block max-h-40 min-h-8 min-w-0 flex-1 resize-none overflow-y-auto border-0 bg-transparent px-1 py-1.5 text-[0.8125rem] leading-5 text-[#242424] outline-none placeholder:text-[#616161]"
        :placeholder="t('chat.typeMessage')"
        :aria-label="t('chat.typeMessage')"
        data-testid="chat-input"
        @keydown="onKeydown"
      />

      <EmojiInsertButton
        :disabled="busy"
        @pick="insertEmoji"
      />
      <button
        type="submit"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[#4f52b2] hover:enabled:bg-[#4f52b2]/10 disabled:cursor-not-allowed disabled:text-[#e1dfdd]"
        :disabled="busy || (!body.trim() && files.length === 0)"
        :aria-label="t('task.sendComment')"
        data-testid="chat-send"
      >
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="currentColor"
          aria-hidden="true"
        ><path d="M3.4 20.4 21 12 3.4 3.6 3.4 10l12 2-12 2z" /></svg>
      </button>
    </form>
    <p
      v-if="body.length > MAX_BODY - 400"
      class="mt-0.5 text-right text-[0.6875rem] text-[#c4314b]"
    >
      {{ body.length }} / {{ MAX_BODY }}
    </p>
  </div>
</template>

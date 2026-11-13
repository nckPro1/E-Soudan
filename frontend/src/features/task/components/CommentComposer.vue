<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { insertAtCaret } from '../../../shared/utils/caret'
import EmojiInsertButton from './EmojiInsertButton.vue'

/**
 * Ô viết bình luận kiểu mạng xã hội: ảnh đại diện, ô nhập bo tròn tự giãn theo nội dung, nút gửi nằm trong ô.
 * Enter gửi, Shift+Enter xuống dòng, Esc huỷ. Đang gõ tiếng Việt/Nhật (IME) thì Enter chỉ để chốt chữ, không gửi.
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    avatarName: string
    placeholder: string
    small?: boolean
    busy?: boolean
    autofocus?: boolean
    max?: number
  }>(),
  { small: false, busy: false, autofocus: false, max: 2000 },
)
const emit = defineEmits<{ 'update:modelValue': [value: string]; submit: []; cancel: [] }>()

const { t } = useI18n()
const input = ref<HTMLTextAreaElement | null>(null)

function grow() {
  const el = input.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

watch(() => props.modelValue, () => nextTick(grow))
onMounted(() => {
  grow()
  if (props.autofocus) input.value?.focus()
})

/** Chèn emoji vào chỗ con trỏ rồi đưa con trỏ về ngay sau emoji. */
function insertEmoji(emoji: string) {
  const el = input.value
  const result = insertAtCaret(props.modelValue, el?.selectionStart ?? null, el?.selectionEnd ?? null, emoji, props.max)
  emit('update:modelValue', result.value)
  void nextTick(() => {
    el?.focus()
    el?.setSelectionRange(result.caret, result.caret)
  })
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    if (props.modelValue.trim() && !props.busy) emit('submit')
  } else if (event.key === 'Escape') {
    emit('cancel')
  }
}
</script>

<template>
  <form
    class="composer flex items-start gap-2"
    @submit.prevent="modelValue.trim() && !busy && emit('submit')"
  >
    <UserAvatar
      :name="avatarName"
      :size="small ? 24 : 32"
    />
    <div class="box relative min-w-0 flex-1">
      <textarea
        ref="input"
        class="input block max-h-40 min-h-9 w-full resize-none overflow-y-auto rounded-[1.125rem] border border-transparent bg-[#f0f2f5] py-2 pl-3.5 pr-[4.5rem] text-sm leading-5 text-[#242424] outline-none placeholder:text-[#616161] focus:border-[#4f52b2] focus:bg-white"
        rows="1"
        :value="modelValue"
        :placeholder="placeholder"
        :aria-label="placeholder"
        :maxlength="max"
        data-testid="composer-input"
        @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
        @keydown="onKeydown"
      />
      <span class="emoji absolute bottom-[0.3125rem] right-9">
        <EmojiInsertButton
          :disabled="busy"
          @pick="insertEmoji"
        />
      </span>
      <button
        type="submit"
        class="send absolute bottom-[0.3125rem] right-1.5 flex h-7 w-7 cursor-pointer items-center justify-center rounded-full border-0 bg-transparent text-[#4f52b2] hover:enabled:bg-[#4f52b2]/10 disabled:cursor-not-allowed disabled:text-[#e1dfdd]"
        :disabled="busy || !modelValue.trim()"
        :aria-label="t('task.sendComment')"
        data-testid="composer-send"
      >
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M3.4 20.4 21 12 3.4 3.6 3.4 10l12 2-12 2z" />
        </svg>
      </button>
    </div>
  </form>
  <p
    v-if="modelValue.length > max - 200"
    class="counter ml-10 mt-0.5 text-[0.6875rem] text-[#c4314b]"
  >
    {{ modelValue.length }} / {{ max }}
  </p>
</template>

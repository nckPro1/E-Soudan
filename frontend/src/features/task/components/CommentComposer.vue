<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import UserAvatar from '../../../shared/components/UserAvatar.vue'

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
    class="composer"
    @submit.prevent="modelValue.trim() && !busy && emit('submit')"
  >
    <UserAvatar
      :name="avatarName"
      :size="small ? 24 : 32"
    />
    <div class="box">
      <textarea
        ref="input"
        class="input"
        rows="1"
        :value="modelValue"
        :placeholder="placeholder"
        :aria-label="placeholder"
        :maxlength="max"
        data-testid="composer-input"
        @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
        @keydown="onKeydown"
      />
      <button
        type="submit"
        class="send"
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
    class="counter"
  >
    {{ modelValue.length }} / {{ max }}
  </p>
</template>

<style scoped>
.composer {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.box {
  position: relative;
  flex: 1;
  min-width: 0;
}

.input {
  display: block;
  width: 100%;
  min-height: 36px;
  max-height: 160px;
  padding: 8px 40px 8px 14px;
  background: var(--color-bubble);
  border: 1px solid transparent;
  border-radius: 18px;
  font-family: inherit;
  font-size: 14px;
  line-height: 20px;
  color: var(--color-text);
  resize: none;
  overflow-y: auto;
}

.input:focus {
  background: var(--color-surface);
  border-color: var(--color-primary);
  outline: 0;
}

.input::placeholder {
  color: var(--color-text-secondary);
}

.send {
  position: absolute;
  right: 6px;
  bottom: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: none;
  border: 0;
  border-radius: 50%;
  color: var(--color-primary);
  cursor: pointer;
}

.send:hover:not(:disabled) {
  background: #e9eef6;
}

.send:disabled {
  color: var(--color-border);
  cursor: not-allowed;
}

.counter {
  margin: 2px 0 0 40px;
  font-size: 11px;
  color: var(--color-danger);
}
</style>

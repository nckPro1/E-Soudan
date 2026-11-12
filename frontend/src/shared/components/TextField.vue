<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    id: string
    label: string
    modelValue: string
    /** 'password' bật nút Hiện/Ẩn mật khẩu */
    type?: 'text' | 'email' | 'password'
    placeholder?: string
    autocomplete?: string
    hint?: string
    disabled?: boolean
    /** Gạch chân đỏ son khi dữ liệu sai (như màn Error State) */
    invalid?: boolean
    required?: boolean
    autofocus?: boolean
  }>(),
  { type: 'text', placeholder: '', autocomplete: 'off', hint: '', disabled: false, invalid: false, required: false, autofocus: false },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const { t } = useI18n()
const revealed = ref(false)
const isPassword = computed(() => props.type === 'password')
const inputType = computed(() => (isPassword.value && revealed.value ? 'text' : props.type))

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="field">
    <label
      :for="id"
      class="label"
    >{{ label }}</label>
    <div class="control">
      <input
        :id="id"
        class="input"
        :class="{ invalid, padded: isPassword }"
        :type="inputType"
        :value="modelValue"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :disabled="disabled"
        :required="required"
        :autofocus="autofocus"
        :aria-invalid="invalid || undefined"
        @input="onInput"
      >
      <button
        v-if="isPassword"
        type="button"
        class="toggle"
        :disabled="disabled"
        @click="revealed = !revealed"
      >
        {{ revealed ? t('auth.hide') : t('auth.show') }}
      </button>
    </div>
    <p
      v-if="hint"
      class="hint"
    >
      {{ hint }}
    </p>
  </div>
</template>

<style scoped>
.label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}

.control {
  position: relative;
  display: flex;
  align-items: center;
}

.input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--color-text);
  transition: border-color 0.15s;
}

.input.padded {
  padding-right: 64px;
}

.input::placeholder {
  color: var(--color-text-secondary);
  opacity: 0.6;
}

.input.invalid {
  border-bottom: 2px solid var(--color-danger);
}

.input:disabled {
  background: var(--color-surface-muted);
  opacity: 0.8;
  cursor: not-allowed;
}

.toggle {
  position: absolute;
  right: 12px;
  padding: 4px;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.toggle:hover:not(:disabled) {
  color: var(--color-text);
}

.toggle:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>

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
      class="field-label"
    >{{ label }}</label>
    <div class="control-wrap relative flex items-center">
      <input
        :id="id"
        class="input control !h-11 w-full !text-sm"
        :class="[invalid ? '!border-b-2 !border-b-[#c4314b]' : '', isPassword ? 'padded pr-16' : '', { invalid }]"
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
        class="toggle absolute right-3 cursor-pointer border-0 bg-transparent p-1 text-xs text-[#616161] hover:text-[#242424] disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="disabled"
        @click="revealed = !revealed"
      >
        {{ revealed ? t('auth.hide') : t('auth.show') }}
      </button>
    </div>
    <p
      v-if="hint"
      class="hint mt-1.5 text-xs text-[#616161]"
    >
      {{ hint }}
    </p>
  </div>
</template>

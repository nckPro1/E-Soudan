<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listUserOptions } from '../../user/api'
import type { UserOption } from '../../user/types'

const props = defineProps<{
  label: string
  /** Vai trò hệ thống của người được chọn: USER cho Leader/Member, MANAGER cho Observer */
  role: 'USER' | 'MANAGER'
  modelValue: number[]
  /** Id không cho chọn (đã được chọn ở ô khác hoặc đã có trong nhóm) */
  excluded?: number[]
  /** Chỉ chọn một người */
  single?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: number[]] }>()

const { t } = useI18n()
const options = ref<UserOption[]>([])
const loading = ref(true)
const failed = ref(false)
const filter = ref('')

onMounted(async () => {
  try {
    options.value = await listUserOptions(props.role)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

const visible = () => {
  const text = filter.value.trim().toLowerCase()
  return options.value.filter(
    (o) =>
      !(props.excluded ?? []).includes(o.id) &&
      (!text || o.fullName.toLowerCase().includes(text) || (o.position ?? '').toLowerCase().includes(text)),
  )
}

function toggle(id: number) {
  const selected = props.modelValue.includes(id)
  if (props.single) {
    emit('update:modelValue', selected ? [] : [id])
  } else {
    emit('update:modelValue', selected ? props.modelValue.filter((v) => v !== id) : [...props.modelValue, id])
  }
}

// Bỏ lựa chọn nếu người đó vừa bị loại khỏi danh sách (chọn ở ô khác)
watch(
  () => props.excluded,
  (excluded) => {
    const kept = props.modelValue.filter((id) => !(excluded ?? []).includes(id))
    if (kept.length !== props.modelValue.length) emit('update:modelValue', kept)
  },
)
</script>

<template>
  <fieldset class="picker m-0 border-0 p-0">
    <legend class="label mb-1.5 p-0 text-[0.8125rem] font-medium">
      {{ label }}
      <span
        v-if="modelValue.length"
        class="count font-normal text-[#616161]"
      >({{ modelValue.length }})</span>
    </legend>
    <input
      v-model="filter"
      type="search"
      class="filter control mb-1.5 !h-8 w-full !px-2.5"
      :placeholder="t('team.pickerSearch')"
      :aria-label="`${label} - ${t('team.pickerSearch')}`"
    >
    <div class="list max-h-36 overflow-y-auto rounded-md border border-[#e1dfdd] bg-white">
      <p
        v-if="loading"
        class="note p-2.5 text-xs text-[#616161]"
      >
        {{ t('common.loading') }}
      </p>
      <p
        v-else-if="failed"
        class="note error p-2.5 text-xs text-[#c4314b]"
      >
        {{ t('errors.UNKNOWN') }}
      </p>
      <p
        v-else-if="visible().length === 0"
        class="note p-2.5 text-xs text-[#616161]"
      >
        {{ t('team.pickerEmpty') }}
      </p>
      <label
        v-for="option in visible()"
        :key="option.id"
        class="row flex cursor-pointer items-center gap-2 px-2.5 py-1.5 text-[0.8125rem] hover:bg-[#f5f5f5]"
      >
        <input
          :type="single ? 'radio' : 'checkbox'"
          :checked="modelValue.includes(option.id)"
          class="accent-[#4f52b2]"
          @change="toggle(option.id)"
        >
        <span>{{ option.fullName }}</span>
        <span
          v-if="option.position"
          class="pos text-xs text-[#616161]"
        >{{ option.position }}</span>
      </label>
    </div>
  </fieldset>
</template>

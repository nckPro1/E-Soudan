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
  <fieldset class="picker">
    <legend class="label">
      {{ label }}
      <span
        v-if="modelValue.length"
        class="count"
      >({{ modelValue.length }})</span>
    </legend>
    <input
      v-model="filter"
      type="search"
      class="filter"
      :placeholder="t('team.pickerSearch')"
      :aria-label="`${label} - ${t('team.pickerSearch')}`"
    >
    <div class="list">
      <p
        v-if="loading"
        class="note"
      >
        {{ t('common.loading') }}
      </p>
      <p
        v-else-if="failed"
        class="note error"
      >
        {{ t('errors.UNKNOWN') }}
      </p>
      <p
        v-else-if="visible().length === 0"
        class="note"
      >
        {{ t('team.pickerEmpty') }}
      </p>
      <label
        v-for="option in visible()"
        :key="option.id"
        class="row"
      >
        <input
          :type="single ? 'radio' : 'checkbox'"
          :checked="modelValue.includes(option.id)"
          @change="toggle(option.id)"
        >
        <span>{{ option.fullName }}</span>
        <span
          v-if="option.position"
          class="pos"
        >{{ option.position }}</span>
      </label>
    </div>
  </fieldset>
</template>

<style scoped>
.picker {
  margin: 0;
  padding: 0;
  border: 0;
}

.label {
  padding: 0;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}

.count {
  color: var(--color-text-secondary);
  font-weight: 400;
}

.filter {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  margin-bottom: 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
}

.list {
  max-height: 140px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 13px;
  cursor: pointer;
}

.row:hover {
  background: var(--color-surface-muted);
}

.pos {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.note {
  margin: 0;
  padding: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.note.error {
  color: var(--color-danger);
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { toApiError } from '../../../shared/api/http'
import UserPicker from '../../team/components/UserPicker.vue'
import * as reportApi from '../api'
import type { CommunityDetail } from '../types'

/** Chủ cộng đồng thêm và bớt thành viên (Leader và Member như nhau). Người được thêm sẽ thấy trang báo cáo. */
const props = defineProps<{ detail: CommunityDetail }>()
const emit = defineEmits<{ close: []; changed: [detail: CommunityDetail] }>()

const { t, te } = useI18n()

const current = ref(props.detail)
const toAdd = ref<number[]>([])
const busy = ref(false)
const errorCode = ref('')

const existing = computed(() => current.value.members.map((m) => m.id))
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function run(action: () => Promise<CommunityDetail>) {
  if (busy.value) return
  busy.value = true
  errorCode.value = ''
  try {
    current.value = await action()
    emit('changed', current.value)
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    busy.value = false
  }
}

async function add() {
  if (toAdd.value.length === 0) return
  await run(() => reportApi.addMembers(current.value.community.id, toAdd.value))
  if (!errorCode.value) toAdd.value = []
}

function remove(userId: number) {
  return run(() => reportApi.removeMember(current.value.community.id, userId))
}
</script>

<template>
  <ModalDialog
    :title="t('report.manageMembers')"
    wide
    @close="emit('close')"
  >
    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <ul class="list mb-4 max-h-60 list-none overflow-y-auto rounded-md border border-[#e1dfdd] p-0">
      <li
        v-for="m in current.members"
        :key="m.id"
        class="row flex items-center gap-3 border-b border-[#edebe9] px-3 py-2 last:border-b-0"
      >
        <UserAvatar
          :name="m.fullName"
          :size="32"
        />
        <span class="name flex-1 text-[0.8125rem]">
          {{ m.fullName }}
          <span
            v-if="m.position"
            class="pos ml-1 text-xs text-[#616161]"
          >· {{ m.position }}</span>
        </span>
        <button
          type="button"
          class="remove cursor-pointer rounded border-0 bg-transparent px-2 py-1 text-xs text-[#c4314b] hover:bg-[#f5f5f5] disabled:opacity-50"
          :disabled="busy"
          @click="remove(m.id)"
        >
          {{ t('report.removeMember') }}
        </button>
      </li>
      <li
        v-if="current.members.length === 0"
        class="empty p-4 text-center text-xs text-[#616161]"
      >
        {{ t('report.noMembers') }}
      </li>
    </ul>

    <UserPicker
      v-model="toAdd"
      :label="t('report.addMembers')"
      role="USER"
      :excluded="existing"
    />

    <div class="buttons mt-4 flex justify-end gap-2">
      <button
        type="button"
        class="btn"
        @click="emit('close')"
      >
        {{ t('report.close') }}
      </button>
      <button
        type="button"
        class="btn btn-primary"
        :disabled="busy || toAdd.length === 0"
        @click="add"
      >
        {{ t('report.addSelected', { count: toAdd.length }) }}
      </button>
    </div>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import TextField from '../../../shared/components/TextField.vue'
import { toApiError } from '../../../shared/api/http'
import UserPicker from '../../team/components/UserPicker.vue'
import * as reportApi from '../api'
import type { CommunityDetail, CommunitySummary } from '../types'

/**
 * Manager tạo cộng đồng (tên, mô tả, chọn luôn người cần thêm) hoặc sửa tên và mô tả.
 * Thêm hoặc bớt thành viên sau này làm ở hộp thoại "Thành viên" của trang cộng đồng.
 */
const props = defineProps<{ community: CommunitySummary | null }>()
const emit = defineEmits<{ close: []; saved: [detail: CommunityDetail] }>()

const { t, te } = useI18n()
const editing = computed(() => props.community !== null)

const name = ref(props.community?.name ?? '')
const description = ref(props.community?.description ?? '')
const memberIds = ref<number[]>([])
const saving = ref(false)
const errorCode = ref('')

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function submit() {
  if (saving.value || !name.value.trim()) return
  saving.value = true
  errorCode.value = ''
  try {
    const desc = description.value.trim() || null
    let saved: CommunityDetail
    if (props.community) {
      saved = await reportApi.updateCommunity(props.community.id, name.value.trim(), desc)
    } else {
      saved = await reportApi.createCommunity(name.value.trim(), desc)
      if (memberIds.value.length > 0) saved = await reportApi.addMembers(saved.community.id, memberIds.value)
    }
    emit('saved', saved)
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ModalDialog
    :title="editing ? t('report.editCommunity') : t('report.createCommunity')"
    wide
    @close="emit('close')"
  >
    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>
    <form
      class="form flex flex-col gap-4"
      @submit.prevent="submit"
    >
      <TextField
        id="community-name"
        v-model="name"
        :label="t('report.communityName')"
        :placeholder="t('report.communityNameExample')"
        :disabled="saving"
        required
      />
      <TextField
        id="community-description"
        v-model="description"
        :label="t('report.communityDescription')"
        :disabled="saving"
      />
      <UserPicker
        v-if="!editing"
        v-model="memberIds"
        :label="t('report.members')"
        role="USER"
      />
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="saving"
          @click="emit('close')"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="saving || !name.trim()"
        >
          {{ saving ? t('changePassword.saving') : t('common.save') }}
        </button>
      </div>
    </form>
  </ModalDialog>
</template>

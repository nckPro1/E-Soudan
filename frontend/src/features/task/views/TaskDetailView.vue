<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import * as teamApi from '../../team/api'
import type { TeamDetail } from '../../team/types'
import * as taskApi from '../api'
import TaskFormDialog from '../components/TaskFormDialog.vue'
import TaskStatusChip from '../components/TaskStatusChip.vue'
import TaskComments from '../components/TaskComments.vue'
import type { HistoryEntry, Submission, Task } from '../types'

const props = defineProps<{ id: number }>()

// Khớp quy định của backend (UploadPolicy); backend vẫn là nơi quyết định, đây chỉ để báo sớm.
const ALLOWED_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'zip']
const MAX_BYTES = 10 * 1024 * 1024
/** Lỗi do người khác vừa thay đổi: báo nhẹ rồi tải lại dữ liệu mới. */
const STALE_CODES = ['VERSION_CONFLICT', 'INVALID_STATE_TRANSITION']

const { t, te, locale } = useI18n()
const auth = useAuthStore()

const task = ref<Task | null>(null)
const team = ref<TeamDetail | null>(null)
const history = ref<HistoryEntry[]>([])
const loading = ref(true)
const acting = ref(false)
const errorCode = ref('')
const notice = ref('')
const uploadError = ref('')

const editing = ref(false)
const revisionOpen = ref(false)
const revisionReason = ref('')
const approveOpen = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const isAssignee = computed(() => task.value?.assignee?.id === auth.user?.id)
const isLeader = computed(
  () => team.value?.members.some((m) => m.userId === auth.user?.id && m.teamRole === 'LEADER') ?? false,
)
const canDownload = computed(() => isAssignee.value || isLeader.value)
const status = computed(() => task.value?.status)
const hasFiles = computed(() => (task.value?.submissions.length ?? 0) > 0)
const needsFileFirst = computed(() => task.value?.requireFile === true && !hasFiles.value)

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

const dateTime = (iso: string) => new Date(iso).toLocaleString(locale.value)
const size = (bytes: number) => (bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`)

async function load() {
  try {
    const loaded = await taskApi.getTask(props.id)
    task.value = loaded
    // Vai trò trong nhóm lấy từ chi tiết nhóm (người xem task cũng xem được nhóm)
    const [teamDetail, entries] = await Promise.all([teamApi.getTeam(loaded.teamId), taskApi.getHistory(props.id)])
    team.value = teamDetail
    history.value = entries
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** Chạy một thao tác; lỗi "người khác vừa đổi" thì báo nhẹ và tải lại thay vì báo lỗi nặng. */
async function run(action: () => Promise<Task>): Promise<boolean> {
  if (acting.value) return false
  acting.value = true
  errorCode.value = ''
  notice.value = ''
  try {
    task.value = await action()
    history.value = await taskApi.getHistory(props.id)
    return true
  } catch (error) {
    const code = toApiError(error).code
    if (STALE_CODES.includes(code)) {
      notice.value = t('task.staleNotice')
      await load()
    } else {
      errorCode.value = code
    }
    return false
  } finally {
    acting.value = false
  }
}

const start = () => task.value && run(() => taskApi.transition(props.id, 'start', task.value!.version))
const submitForReview = () =>
  task.value && run(() => taskApi.transition(props.id, 'submit-for-review', task.value!.version))

async function approve() {
  if (!task.value) return
  const version = task.value.version
  await run(() => taskApi.transition(props.id, 'approve', version))
  approveOpen.value = false
}

async function sendRevision() {
  if (!task.value || !revisionReason.value.trim()) return
  const version = task.value.version
  const reason = revisionReason.value.trim()
  if (await run(() => taskApi.requestRevision(props.id, version, reason))) {
    revisionOpen.value = false
    revisionReason.value = ''
  } else {
    revisionOpen.value = false
  }
}

function onSaved(saved: Task) {
  task.value = saved
  editing.value = false
  void taskApi.getHistory(props.id).then((h) => (history.value = h))
}

function validateFile(file: File): string {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.includes(ext)) return t('task.fileTypeError')
  if (file.size > MAX_BYTES) return t('task.fileSizeError')
  if (file.size === 0) return t('errors.INVALID_FILE')
  return ''
}

async function onFileChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !task.value) return
  uploadError.value = validateFile(file)
  if (uploadError.value) return
  acting.value = true
  try {
    await taskApi.uploadSubmission(props.id, file)
    task.value = await taskApi.getTask(props.id)
  } catch (error) {
    const code = toApiError(error).code
    uploadError.value = te(`errors.${code}`) ? t(`errors.${code}`) : t('errors.UNKNOWN')
  } finally {
    acting.value = false
  }
}

async function download(file: Submission) {
  try {
    const blob = await taskApi.downloadSubmission(props.id, file.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    errorCode.value = toApiError(error).code
  }
}
</script>

<template>
  <div>
    <RouterLink
      v-if="task"
      :to="{ name: 'team-detail', params: { id: task.teamId } }"
      class="back mb-4 inline-block text-[0.8125rem] text-[#616161] hover:text-[#242424]"
    >
      ← {{ task.teamName }}
    </RouterLink>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>
    <AlertBanner
      v-if="notice"
      tone="info"
    >
      {{ notice }}
    </AlertBanner>

    <p
      v-if="loading"
      class="muted text-[0.8125rem] text-[#616161]"
    >
      {{ t('common.loading') }}
    </p>

    <template v-else-if="task">
      <div class="head mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 class="page-title">
            {{ task.title }}
          </h1>
          <p class="muted text-[0.8125rem] text-[#616161]">
            {{ t('task.assignedBy', { name: task.assignedBy?.fullName ?? '' }) }}
          </p>
        </div>
        <div class="head-actions flex items-center gap-2">
          <TaskStatusChip :status="task.status" />
          <button
            v-if="isLeader && status !== 'DONE'"
            type="button"
            class="btn"
            @click="editing = true"
          >
            {{ t('common.edit') }}
          </button>
        </div>
      </div>

      <AlertBanner
        v-if="task.revisionReason && status === 'IN_PROGRESS'"
      >
        <strong>{{ t('task.revisionRequested') }}</strong> {{ task.revisionReason }}
      </AlertBanner>

      <section class="card panel max-w-[40rem] p-6">
        <dl class="grid grid-cols-[140px_1fr] gap-x-4 gap-y-2 text-[0.8125rem]">
          <dt class="text-[#616161]">
            {{ t('task.assignee') }}
          </dt>
          <dd>{{ task.assignee?.fullName }}</dd>
          <dt class="text-[#616161]">
            {{ t('task.dueDate') }}
          </dt>
          <dd>{{ task.dueDate ?? '—' }}</dd>
          <dt class="text-[#616161]">
            {{ t('task.priority') }}
          </dt>
          <dd>{{ t(`task.priorities.${task.priority}`) }}</dd>
          <dt class="text-[#616161]">
            {{ t('task.requireFile') }}
          </dt>
          <dd>{{ task.requireFile ? t('common.yes') : t('common.no') }}</dd>
        </dl>
        <p
          v-if="task.description"
          class="description mt-4 whitespace-pre-wrap border-t border-[#e1dfdd] pt-4 text-[0.8125rem] leading-relaxed"
        >
          {{ task.description }}
        </p>
      </section>

      <!-- Hành động theo vai trò và trạng thái. Backend vẫn là nơi quyết định. -->
      <section
        v-if="(isAssignee && (status === 'ASSIGNED' || status === 'IN_PROGRESS')) || (isLeader && status === 'PENDING_REVIEW')"
        class="actions mb-4 mt-6 flex flex-wrap items-center gap-3"
      >
        <button
          v-if="isAssignee && status === 'ASSIGNED'"
          type="button"
          class="btn btn-primary"
          :disabled="acting"
          @click="start"
        >
          {{ t('task.start') }}
        </button>
        <template v-if="isAssignee && status === 'IN_PROGRESS'">
          <button
            type="button"
            class="btn"
            :disabled="acting"
            @click="fileInput?.click()"
          >
            {{ t('task.uploadFile') }}
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="acting || needsFileFirst"
            @click="submitForReview"
          >
            {{ t('task.submitForReview') }}
          </button>
          <span
            v-if="needsFileFirst"
            class="muted text-[0.8125rem] text-[#616161]"
          >{{ t('task.fileRequiredHint') }}</span>
        </template>
        <template v-if="isLeader && status === 'PENDING_REVIEW'">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="acting"
            @click="approveOpen = true"
          >
            {{ t('task.approve') }}
          </button>
          <button
            type="button"
            class="btn"
            :disabled="acting"
            @click="revisionOpen = true"
          >
            {{ t('task.requestRevision') }}
          </button>
        </template>
      </section>

      <input
        ref="fileInput"
        type="file"
        class="hidden"
        data-testid="file-input"
        :accept="ALLOWED_EXTENSIONS.map((e) => `.${e}`).join(',')"
        @change="onFileChosen"
      >
      <AlertBanner v-if="uploadError">
        {{ uploadError }}
      </AlertBanner>

      <section>
        <h2 class="mb-3 mt-8 text-base font-bold">
          {{ t('task.submissions') }} ({{ task.submissions.length }})
        </h2>
        <p
          v-if="task.submissions.length === 0"
          class="muted text-[0.8125rem] text-[#616161]"
        >
          {{ t('task.noSubmissions') }}
        </p>
        <ul
          v-else
          class="files m-0 list-none p-0 text-[0.8125rem] [&>li]:py-1.5"
        >
          <li
            v-for="file in task.submissions"
            :key="file.id"
          >
            <button
              v-if="canDownload"
              type="button"
              class="link cursor-pointer"
              @click="download(file)"
            >
              {{ file.fileName }}
            </button>
            <span v-else>{{ file.fileName }}</span>
            <span class="muted text-[0.8125rem] text-[#616161]"> · {{ size(file.sizeBytes) }} · {{ dateTime(file.uploadedAt) }}</span>
          </li>
        </ul>
      </section>

      <section>
        <h2 class="mb-3 mt-8 text-base font-bold">
          {{ t('task.history') }}
        </h2>
        <ol class="timeline m-0 list-none border-l-2 border-[#e1dfdd] p-0 pl-4 [&>li]:relative [&>li]:flex [&>li]:gap-4 [&>li]:pb-4 [&>li]:pl-4 [&>li]:text-[0.8125rem] max-md:[&>li]:flex-col [&>li]:before:absolute [&>li]:before:-left-[1.375rem] [&>li]:before:top-[0.3125rem] [&>li]:before:h-2 [&>li]:before:w-2 [&>li]:before:rounded-full [&>li]:before:bg-[#4f52b2] [&>li]:before:content-['']">
          <li
            v-for="entry in history"
            :key="entry.id"
          >
            <div class="when w-40 shrink-0 text-[#616161]">
              {{ dateTime(entry.changedAt) }}
            </div>
            <div>
              <TaskStatusChip :status="entry.toStatus" />
              <span class="muted text-[0.8125rem] text-[#616161]"> {{ entry.changedBy?.fullName }}</span>
              <p
                v-if="entry.note"
                class="note mt-1 whitespace-pre-wrap"
              >
                {{ entry.note }}
              </p>
            </div>
          </li>
        </ol>
      </section>

      <TaskComments
        :task-id="task.id"
        :is-leader="isLeader"
      />
    </template>

    <TaskFormDialog
      v-if="editing && task && team"
      :team-id="task.teamId"
      :task="task"
      :members="team.members"
      @close="editing = false"
      @saved="onSaved"
    />

    <ModalDialog
      v-if="approveOpen"
      :title="t('task.approve')"
      @close="approveOpen = false"
    >
      <p class="confirm-text mb-4 text-sm">
        {{ t('task.approveConfirm') }}
      </p>
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="acting"
          @click="approveOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="acting"
          @click="approve"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>

    <ModalDialog
      v-if="revisionOpen"
      :title="t('task.requestRevision')"
      @close="revisionOpen = false"
    >
      <label
        for="revision-reason"
        class="label field-label"
      >{{ t('task.revisionReason') }}</label>
      <textarea
        id="revision-reason"
        v-model="revisionReason"
        class="textarea control mb-2 w-full resize-y"
        rows="4"
        maxlength="1000"
      />
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="acting"
          @click="revisionOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="acting || !revisionReason.trim()"
          @click="sendRevision"
        >
          {{ t('task.sendBack') }}
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

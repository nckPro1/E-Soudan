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
      class="back"
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
      class="muted"
    >
      {{ t('common.loading') }}
    </p>

    <template v-else-if="task">
      <div class="head">
        <div>
          <h1>{{ task.title }}</h1>
          <p class="muted">
            {{ t('task.assignedBy', { name: task.assignedBy?.fullName ?? '' }) }}
          </p>
        </div>
        <div class="head-actions">
          <TaskStatusChip :status="task.status" />
          <button
            v-if="isLeader && status !== 'DONE'"
            type="button"
            class="btn secondary"
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

      <section class="card">
        <dl>
          <dt>{{ t('task.assignee') }}</dt>
          <dd>{{ task.assignee?.fullName }}</dd>
          <dt>{{ t('task.dueDate') }}</dt>
          <dd>{{ task.dueDate ?? '—' }}</dd>
          <dt>{{ t('task.priority') }}</dt>
          <dd>{{ t(`task.priorities.${task.priority}`) }}</dd>
          <dt>{{ t('task.requireFile') }}</dt>
          <dd>{{ task.requireFile ? t('common.yes') : t('common.no') }}</dd>
        </dl>
        <p
          v-if="task.description"
          class="description"
        >
          {{ task.description }}
        </p>
      </section>

      <!-- Hành động theo vai trò và trạng thái. Backend vẫn là nơi quyết định. -->
      <section
        v-if="(isAssignee && (status === 'ASSIGNED' || status === 'IN_PROGRESS')) || (isLeader && status === 'PENDING_REVIEW')"
        class="actions"
      >
        <button
          v-if="isAssignee && status === 'ASSIGNED'"
          type="button"
          class="btn primary"
          :disabled="acting"
          @click="start"
        >
          {{ t('task.start') }}
        </button>
        <template v-if="isAssignee && status === 'IN_PROGRESS'">
          <button
            type="button"
            class="btn secondary"
            :disabled="acting"
            @click="fileInput?.click()"
          >
            {{ t('task.uploadFile') }}
          </button>
          <button
            type="button"
            class="btn primary"
            :disabled="acting || needsFileFirst"
            @click="submitForReview"
          >
            {{ t('task.submitForReview') }}
          </button>
          <span
            v-if="needsFileFirst"
            class="muted"
          >{{ t('task.fileRequiredHint') }}</span>
        </template>
        <template v-if="isLeader && status === 'PENDING_REVIEW'">
          <button
            type="button"
            class="btn primary"
            :disabled="acting"
            @click="approveOpen = true"
          >
            {{ t('task.approve') }}
          </button>
          <button
            type="button"
            class="btn secondary"
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
        <h2>{{ t('task.submissions') }} ({{ task.submissions.length }})</h2>
        <p
          v-if="task.submissions.length === 0"
          class="muted"
        >
          {{ t('task.noSubmissions') }}
        </p>
        <ul
          v-else
          class="files"
        >
          <li
            v-for="file in task.submissions"
            :key="file.id"
          >
            <button
              v-if="canDownload"
              type="button"
              class="link"
              @click="download(file)"
            >
              {{ file.fileName }}
            </button>
            <span v-else>{{ file.fileName }}</span>
            <span class="muted"> · {{ size(file.sizeBytes) }} · {{ dateTime(file.uploadedAt) }}</span>
          </li>
        </ul>
      </section>

      <section>
        <h2>{{ t('task.history') }}</h2>
        <ol class="timeline">
          <li
            v-for="entry in history"
            :key="entry.id"
          >
            <div class="when">
              {{ dateTime(entry.changedAt) }}
            </div>
            <div>
              <TaskStatusChip :status="entry.toStatus" />
              <span class="muted"> {{ entry.changedBy?.fullName }}</span>
              <p
                v-if="entry.note"
                class="note"
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
      <p class="confirm-text">
        {{ t('task.approveConfirm') }}
      </p>
      <div class="buttons">
        <button
          type="button"
          class="btn secondary"
          :disabled="acting"
          @click="approveOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn primary"
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
        class="label"
      >{{ t('task.revisionReason') }}</label>
      <textarea
        id="revision-reason"
        v-model="revisionReason"
        class="textarea"
        rows="4"
        maxlength="1000"
      />
      <div class="buttons">
        <button
          type="button"
          class="btn secondary"
          :disabled="acting"
          @click="revisionOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn primary"
          :disabled="acting || !revisionReason.trim()"
          @click="sendRevision"
        >
          {{ t('task.sendBack') }}
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--color-primary);
  text-decoration: none;
}

.back:hover {
  text-decoration: underline;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

h1 {
  margin: 0 0 4px;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

h2 {
  margin: 32px 0 12px;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 500;
}

.muted {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.card {
  max-width: 640px;
  padding: 24px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

dl {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 8px 16px;
  margin: 0;
  font-size: 14px;
}

dt {
  color: var(--color-text-secondary);
}

dd {
  margin: 0;
}

.description {
  margin: 16px 0 0;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 24px 0 16px;
}

.hidden {
  display: none;
}

.files {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 14px;
}

.files li {
  padding: 6px 0;
}

.link {
  padding: 0;
  background: none;
  border: 0;
  font-size: 14px;
  color: var(--color-primary);
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.timeline {
  margin: 0;
  padding: 0 0 0 16px;
  list-style: none;
  border-left: 2px solid var(--color-border);
}

.timeline li {
  position: relative;
  display: flex;
  gap: 16px;
  padding: 0 0 16px 16px;
  font-size: 13px;
}

.timeline li::before {
  content: '';
  position: absolute;
  left: -22px;
  top: 5px;
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: 50%;
}

.when {
  flex-shrink: 0;
  width: 160px;
  color: var(--color-text-secondary);
}

.note {
  margin: 4px 0 0;
  white-space: pre-wrap;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}

.textarea {
  width: 100%;
  padding: 10px 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  resize: vertical;
}

.confirm-text {
  margin: 0 0 24px;
  font-size: 14px;
  line-height: 1.6;
}

.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.btn {
  height: 36px;
  padding: 0 16px;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: #fff;
}

.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.secondary {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

@media (max-width: 720px) {
  .timeline li {
    flex-direction: column;
    gap: 4px;
  }

  .when {
    width: auto;
  }

  dl {
    grid-template-columns: 110px 1fr;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import * as taskApi from '../api'
import type { TaskComment } from '../types'
import CommentItem from './CommentItem.vue'

/**
 * Khu bình luận của một task: viết, trả lời (một cấp), sửa bình luận của mình, xoá (của mình, hoặc của người khác
 * nếu là Leader) và thả biểu cảm. Backend vẫn là nơi quyết định mọi quyền.
 */
const props = defineProps<{ taskId: number; isLeader: boolean }>()

const MAX = 2000
const { t, te } = useI18n()
const auth = useAuthStore()

const comments = ref<TaskComment[]>([])
const loading = ref(true)
const error = ref('')
const newText = ref('')
const posting = ref(false)
const busy = ref(false)

const replyTo = ref<TaskComment | null>(null)
const replyText = ref('')
const editingId = ref<number | null>(null)
const deleteTarget = ref<TaskComment | null>(null)

const roots = computed(() => comments.value.filter((c) => c.parentId === null))
const repliesOf = (id: number) => comments.value.filter((c) => c.parentId === id)
const liveCount = computed(() => comments.value.filter((c) => !c.deleted).length)
const isMine = (c: TaskComment) => c.author?.id === auth.user?.id

function message(err: unknown): string {
  const code = toApiError(err).code
  return te(`errors.${code}`) ? t(`errors.${code}`) : t('errors.UNKNOWN')
}

async function load() {
  try {
    comments.value = await taskApi.listComments(props.taskId)
  } catch (err) {
    error.value = message(err)
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** Thay một bình luận bằng bản mới server trả về (giữ nguyên vị trí). */
function replace(updated: TaskComment) {
  comments.value = comments.value.map((c) => (c.id === updated.id ? updated : c))
}

async function post(text: string, replyToId?: number): Promise<boolean> {
  const body = text.trim()
  if (!body || posting.value) return false
  posting.value = true
  error.value = ''
  try {
    comments.value = [...comments.value, await taskApi.addComment(props.taskId, body, replyToId)]
    return true
  } catch (err) {
    error.value = message(err)
    return false
  } finally {
    posting.value = false
  }
}

async function submitNew() {
  if (await post(newText.value)) newText.value = ''
}

async function submitReply() {
  if (replyTo.value && (await post(replyText.value, replyTo.value.id))) {
    replyTo.value = null
    replyText.value = ''
  }
}

function startReply(comment: TaskComment) {
  editingId.value = null
  replyTo.value = comment
  replyText.value = ''
}

async function saveEdit(comment: TaskComment, body: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    replace(await taskApi.editComment(props.taskId, comment.id, body))
    editingId.value = null
  } catch (err) {
    error.value = message(err)
  } finally {
    busy.value = false
  }
}

async function toggleReaction(comment: TaskComment, emoji: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const mine = comment.reactions.some((r) => r.emoji === emoji && r.reactedByMe)
    replace(
      mine
        ? await taskApi.removeReaction(props.taskId, comment.id, emoji)
        : await taskApi.reactToComment(props.taskId, comment.id, emoji),
    )
  } catch (err) {
    error.value = message(err)
  } finally {
    busy.value = false
  }
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target) return
  try {
    await taskApi.deleteComment(props.taskId, target.id)
  } catch {
    error.value = t('task.commentDeleteFailed')
  } finally {
    deleteTarget.value = null
    // Tải lại để bình luận gốc đã xoá hiện đúng dạng "đã xoá" hoặc biến mất nếu không còn trả lời
    await load()
  }
}
</script>

<template>
  <section>
    <h2>{{ t('task.comments') }} ({{ liveCount }})</h2>
    <p
      v-if="!loading && comments.length === 0"
      class="muted"
    >
      {{ t('task.noComments') }}
    </p>

    <AlertBanner v-if="error">
      {{ error }}
    </AlertBanner>

    <ul class="threads">
      <li
        v-for="root in roots"
        :key="root.id"
      >
        <CommentItem
          :comment="root"
          :is-mine="isMine(root)"
          :can-delete="isMine(root) || isLeader"
          :editing="editingId === root.id"
          :busy="busy"
          @reply="startReply(root)"
          @start-edit="editingId = root.id"
          @cancel-edit="editingId = null"
          @save-edit="saveEdit(root, $event)"
          @delete="deleteTarget = root"
          @toggle="toggleReaction(root, $event)"
        />

        <ul class="replies">
          <li
            v-for="reply in repliesOf(root.id)"
            :key="reply.id"
          >
            <CommentItem
              :comment="reply"
              :is-mine="isMine(reply)"
              :can-delete="isMine(reply) || isLeader"
              :editing="editingId === reply.id"
              :busy="busy"
              @reply="startReply(reply)"
              @start-edit="editingId = reply.id"
              @cancel-edit="editingId = null"
              @save-edit="saveEdit(reply, $event)"
              @delete="deleteTarget = reply"
              @toggle="toggleReaction(reply, $event)"
            />
          </li>
        </ul>

        <!-- Form trả lời hiện ngay dưới chuỗi đang được trả lời -->
        <form
          v-if="replyTo && (replyTo.id === root.id || replyTo.parentId === root.id)"
          class="reply-form"
          @submit.prevent="submitReply"
        >
          <label
            :for="`reply-${root.id}`"
            class="label"
          >{{ t('task.replyingTo', { name: replyTo.author?.fullName ?? '' }) }}</label>
          <textarea
            :id="`reply-${root.id}`"
            v-model="replyText"
            class="textarea"
            rows="2"
            :maxlength="MAX"
            data-testid="reply-input"
          />
          <div class="row">
            <button
              type="button"
              class="btn"
              @click="replyTo = null"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              type="submit"
              class="btn primary"
              :disabled="posting || !replyText.trim()"
            >
              {{ t('task.sendComment') }}
            </button>
          </div>
        </form>
      </li>
    </ul>

    <form
      class="new-form"
      @submit.prevent="submitNew"
    >
      <label
        for="new-comment"
        class="label"
      >{{ t('task.addComment') }}</label>
      <textarea
        id="new-comment"
        v-model="newText"
        class="textarea"
        rows="3"
        :maxlength="MAX"
        :placeholder="t('task.commentPlaceholder')"
      />
      <div class="row between">
        <span class="muted">{{ newText.length }} / {{ MAX }}</span>
        <button
          type="submit"
          class="btn primary"
          :disabled="posting || !newText.trim()"
        >
          {{ t('task.sendComment') }}
        </button>
      </div>
    </form>

    <ModalDialog
      v-if="deleteTarget"
      :title="t('task.deleteCommentTitle')"
      @close="deleteTarget = null"
    >
      <p class="confirm-text">
        {{ t('task.deleteCommentConfirm') }}
      </p>
      <div class="row">
        <button
          type="button"
          class="btn"
          @click="deleteTarget = null"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn primary"
          @click="confirmDelete"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </section>
</template>

<style scoped>
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

.threads,
.replies {
  margin: 0;
  padding: 0;
  list-style: none;
}

.threads > li {
  margin-bottom: 12px;
}

.replies {
  margin-left: 24px;
  padding-left: 12px;
  border-left: 2px solid var(--color-border);
}

.replies > li {
  margin-top: 8px;
}

.reply-form,
.new-form {
  max-width: 640px;
  margin: 8px 0 0 24px;
}

.new-form {
  margin: 16px 0 0;
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

.row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.row.between {
  align-items: center;
  justify-content: space-between;
}

.btn {
  height: 34px;
  padding: 0 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.confirm-text {
  margin: 0 0 16px;
  font-size: 14px;
}
</style>

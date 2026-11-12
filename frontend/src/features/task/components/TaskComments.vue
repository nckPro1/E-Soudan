<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import * as taskApi from '../api'
import type { TaskComment } from '../types'
import CommentComposer from './CommentComposer.vue'
import CommentItem from './CommentItem.vue'

/**
 * Khu bình luận của một task, kiểu Facebook: bong bóng bình luận, biểu cảm, phản hồi một cấp, sửa bình luận của
 * mình, xoá (của mình, hoặc của người khác nếu là Leader). Backend vẫn là nơi quyết định mọi quyền.
 */
const props = defineProps<{ taskId: number; isLeader: boolean }>()

/** Chuỗi có nhiều hơn số này phản hồi thì thu gọn, chỉ hiện phản hồi mới nhất kèm nút "Xem ... phản hồi". */
const COLLAPSE_OVER = 2

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
const expanded = ref<Set<number>>(new Set())

const myName = computed(() => auth.user?.fullName ?? '')
const roots = computed(() => comments.value.filter((c) => c.parentId === null))
const repliesOf = (id: number) => comments.value.filter((c) => c.parentId === id)
const liveCount = computed(() => comments.value.filter((c) => !c.deleted).length)
const isMine = (c: TaskComment) => c.author?.id === auth.user?.id

/** Các phản hồi đang hiện của một chuỗi (thu gọn thì chỉ phản hồi mới nhất). */
function visibleReplies(root: TaskComment): TaskComment[] {
  const all = repliesOf(root.id)
  return all.length > COLLAPSE_OVER && !expanded.value.has(root.id) ? all.slice(-1) : all
}
const hiddenCount = (root: TaskComment) => repliesOf(root.id).length - visibleReplies(root).length
const replyFormOpenFor = (root: TaskComment) =>
  replyTo.value !== null && (replyTo.value.id === root.id || replyTo.value.parentId === root.id)

function expand(rootId: number) {
  expanded.value = new Set(expanded.value).add(rootId)
}

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

async function post(text: string, replyToId?: number): Promise<TaskComment | null> {
  const body = text.trim()
  if (!body || posting.value) return null
  posting.value = true
  error.value = ''
  try {
    const saved = await taskApi.addComment(props.taskId, body, replyToId)
    comments.value = [...comments.value, saved]
    return saved
  } catch (err) {
    error.value = message(err)
    return null
  } finally {
    posting.value = false
  }
}

async function submitNew() {
  if (await post(newText.value)) newText.value = ''
}

async function submitReply() {
  const target = replyTo.value
  if (target && (await post(replyText.value, target.id))) {
    expand(target.parentId ?? target.id)
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
        class="thread"
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

        <div
          v-if="repliesOf(root.id).length > 0 || replyFormOpenFor(root)"
          class="replies"
        >
          <button
            v-if="hiddenCount(root) > 0"
            type="button"
            class="view-replies"
            data-testid="view-replies"
            @click="expand(root.id)"
          >
            {{ t('task.viewReplies', { count: hiddenCount(root) }) }}
          </button>

          <CommentItem
            v-for="reply in visibleReplies(root)"
            :key="reply.id"
            small
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

          <div
            v-if="replyFormOpenFor(root)"
            class="reply-form"
          >
            <CommentComposer
              v-model="replyText"
              small
              autofocus
              :avatar-name="myName"
              :busy="posting"
              :placeholder="t('task.writeReply', { name: replyTo?.author?.fullName ?? '' })"
              @submit="submitReply"
              @cancel="replyTo = null"
            />
            <p class="cancel-reply">
              {{ t('task.escToCancel') }}
              <button
                type="button"
                class="link"
                @click="replyTo = null"
              >
                {{ t('common.cancel') }}
              </button>
            </p>
          </div>
        </div>
      </li>
    </ul>

    <div class="new-comment">
      <CommentComposer
        v-model="newText"
        :avatar-name="myName"
        :busy="posting"
        :placeholder="t('task.writeComment')"
        @submit="submitNew"
      />
    </div>

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
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.threads {
  max-width: 720px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.thread {
  margin-bottom: 16px;
}

/* Phản hồi thụt vào bằng đúng bề rộng ảnh đại diện + khoảng cách */
.replies {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 8px 0 0 40px;
}

.view-replies {
  align-self: flex-start;
  padding: 0;
  background: none;
  border: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.view-replies:hover {
  text-decoration: underline;
}

.cancel-reply {
  margin: 4px 0 0 32px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.link {
  margin-left: 6px;
  padding: 0;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.new-comment {
  max-width: 720px;
  margin-top: 8px;
}

.row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
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

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import CommentComposer from '../../task/components/CommentComposer.vue'
import CommentItem from '../../task/components/CommentItem.vue'
import * as reportApi from '../api'
import type { ReportComment } from '../types'

/**
 * Bình luận dưới một báo cáo, dùng lại giao diện bình luận kiểu Facebook của task: phản hồi một cấp, sửa bình luận
 * của mình, xoá (của mình, hoặc của người khác nếu là chủ cộng đồng), thả biểu cảm.
 * Backend vẫn là nơi quyết định mọi quyền.
 */
const props = defineProps<{ postId: number; canModerate: boolean }>()
const emit = defineEmits<{ changed: [count: number] }>()

const COLLAPSE_OVER = 2

const { t, te } = useI18n()
const auth = useAuthStore()

const comments = ref<ReportComment[]>([])
const loading = ref(true)
const error = ref('')
const newText = ref('')
const posting = ref(false)
const busy = ref(false)

const replyTo = ref<ReportComment | null>(null)
const replyText = ref('')
const editingId = ref<number | null>(null)
const deleteTarget = ref<ReportComment | null>(null)
const expanded = ref<Set<number>>(new Set())

const myName = computed(() => auth.user?.fullName ?? '')
const roots = computed(() => comments.value.filter((c) => c.parentId === null))
const repliesOf = (id: number) => comments.value.filter((c) => c.parentId === id)
const isMine = (c: ReportComment) => c.author?.id === auth.user?.id

function announce() {
  emit('changed', comments.value.filter((c) => !c.deleted).length)
}

function visibleReplies(root: ReportComment): ReportComment[] {
  const all = repliesOf(root.id)
  return all.length > COLLAPSE_OVER && !expanded.value.has(root.id) ? all.slice(-1) : all
}
const hiddenCount = (root: ReportComment) => repliesOf(root.id).length - visibleReplies(root).length
const replyFormOpenFor = (root: ReportComment) =>
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
    comments.value = await reportApi.listComments(props.postId)
    announce()
  } catch (err) {
    error.value = message(err)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function replace(updated: ReportComment) {
  comments.value = comments.value.map((c) => (c.id === updated.id ? updated : c))
}

async function post(text: string, replyToId?: number): Promise<ReportComment | null> {
  const body = text.trim()
  if (!body || posting.value) return null
  posting.value = true
  error.value = ''
  try {
    const saved = await reportApi.addComment(props.postId, body, replyToId)
    comments.value = [...comments.value, saved]
    announce()
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

function startReply(comment: ReportComment) {
  editingId.value = null
  replyTo.value = comment
  replyText.value = ''
}

async function saveEdit(comment: ReportComment, body: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    replace(await reportApi.editComment(props.postId, comment.id, body))
    editingId.value = null
  } catch (err) {
    error.value = message(err)
  } finally {
    busy.value = false
  }
}

async function toggleReaction(comment: ReportComment, emoji: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const mine = comment.reactions.some((r) => r.emoji === emoji && r.reactedByMe)
    replace(
      mine
        ? await reportApi.removeCommentReaction(props.postId, comment.id, emoji)
        : await reportApi.reactToComment(props.postId, comment.id, emoji),
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
    await reportApi.deleteComment(props.postId, target.id)
  } catch {
    error.value = t('task.commentDeleteFailed')
  } finally {
    deleteTarget.value = null
    await load()
  }
}
</script>

<template>
  <section class="comments border-t border-[#e1dfdd] pt-3">
    <AlertBanner v-if="error">
      {{ error }}
    </AlertBanner>

    <p
      v-if="!loading && comments.length === 0"
      class="muted mb-3 text-[0.8125rem] text-[#616161]"
    >
      {{ t('task.noComments') }}
    </p>

    <ul class="threads m-0 list-none p-0">
      <li
        v-for="root in roots"
        :key="root.id"
        class="thread mb-4"
      >
        <CommentItem
          :comment="root"
          :is-mine="isMine(root)"
          :can-delete="isMine(root) || canModerate"
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
          class="replies ml-10 mt-2 flex flex-col gap-2"
        >
          <button
            v-if="hiddenCount(root) > 0"
            type="button"
            class="view-replies cursor-pointer self-start border-0 bg-transparent p-0 text-[0.8125rem] font-semibold text-[#616161] hover:underline"
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
            :can-delete="isMine(reply) || canModerate"
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
            <p class="cancel-reply ml-8 mt-1 text-xs text-[#616161]">
              {{ t('task.escToCancel') }}
              <button
                type="button"
                class="link ml-1.5 cursor-pointer border-0 bg-transparent p-0 text-xs text-[#4f52b2]"
                @click="replyTo = null"
              >
                {{ t('common.cancel') }}
              </button>
            </p>
          </div>
        </div>
      </li>
    </ul>

    <div class="new-comment mt-2">
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
      <p class="confirm-text mb-4 text-sm">
        {{ t('task.deleteCommentConfirm') }}
      </p>
      <div class="row mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          @click="deleteTarget = null"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          @click="confirmDelete"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </section>
</template>

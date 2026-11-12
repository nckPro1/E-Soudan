<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { timeAgo } from '../../../shared/utils/time'
import type { TaskComment } from '../types'
import ReactionPicker from './ReactionPicker.vue'
import ReactionSummary from './ReactionSummary.vue'

/**
 * Một bình luận kiểu Facebook: ảnh đại diện, bong bóng (tên + nội dung), tóm tắt biểu cảm gắn ở góc bong bóng,
 * và hàng thao tác nhỏ bên dưới (Thích · Cảm xúc · Phản hồi · Sửa · Xoá · thời gian).
 * Không tự thả biểu cảm vào bình luận của mình: với bình luận của mình không có "Thích"/"Cảm xúc".
 * Form trả lời do component cha dựng bên dưới.
 */
const props = defineProps<{
  comment: TaskComment
  isMine: boolean
  canDelete: boolean
  editing: boolean
  busy: boolean
  /** Bình luận trả lời: ảnh đại diện nhỏ hơn */
  small?: boolean
}>()
const emit = defineEmits<{
  reply: []
  startEdit: []
  cancelEdit: []
  saveEdit: [body: string]
  delete: []
  toggle: [emoji: string]
}>()

const LIKE = '👍'
const MAX = 2000
const { t, locale } = useI18n()
const draft = ref('')
const editInput = ref<HTMLTextAreaElement | null>(null)

// Mở chế độ sửa thì điền sẵn nội dung và đưa con trỏ vào cuối
watch(
  () => props.editing,
  async (value) => {
    if (!value) return
    draft.value = props.comment.body ?? ''
    await nextTick()
    editInput.value?.focus()
    editInput.value?.setSelectionRange(draft.value.length, draft.value.length)
  },
  { immediate: true },
)

const liked = computed(() => props.comment.reactions.some((r) => r.emoji === LIKE && r.reactedByMe))
const when = computed(() => timeAgo(props.comment.createdAt, locale.value) ?? t('task.justNow'))
const fullDate = computed(() => new Date(props.comment.createdAt).toLocaleString(locale.value))
const authorName = computed(() => props.comment.author?.fullName ?? '')

function onEditKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    if (draft.value.trim() && !props.busy) emit('saveEdit', draft.value)
  } else if (event.key === 'Escape') {
    emit('cancelEdit')
  }
}
</script>

<template>
  <article
    class="comment"
    :class="{ small }"
  >
    <UserAvatar
      v-if="!comment.deleted"
      :name="authorName"
      :size="small ? 24 : 32"
    />
    <span
      v-else
      class="ghost"
      :class="{ small }"
    />

    <div class="main">
      <div
        v-if="comment.deleted"
        class="bubble gone"
      >
        {{ t('task.commentDeleted') }}
      </div>

      <template v-else>
        <form
          v-if="editing"
          class="edit"
          @submit.prevent="draft.trim() && !busy && emit('saveEdit', draft)"
        >
          <textarea
            ref="editInput"
            v-model="draft"
            class="edit-input"
            rows="2"
            :maxlength="MAX"
            :aria-label="t('common.edit')"
            data-testid="edit-input"
            @keydown="onEditKey"
          />
          <p class="hint">
            {{ t('task.editHint') }}
            <button
              type="button"
              class="link"
              @click="emit('cancelEdit')"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              type="submit"
              class="link strong"
              :disabled="busy || !draft.trim()"
            >
              {{ t('common.save') }}
            </button>
          </p>
        </form>

        <template v-else>
          <div class="bubble-wrap">
            <div class="bubble">
              <strong class="name">{{ authorName }}</strong>
              <!-- Văn bản thuần: {{ }} luôn được Vue escape nên không chạy được HTML hay script -->
              <p class="text">
                {{ comment.body }}
              </p>
            </div>
            <ReactionSummary
              v-if="comment.reactions.length > 0"
              class="reactions"
              :reactions="comment.reactions"
            />
          </div>

          <div class="meta">
            <template v-if="!isMine">
              <button
                type="button"
                class="action"
                :class="{ active: liked }"
                :disabled="busy"
                :aria-pressed="liked"
                data-testid="like"
                @click="emit('toggle', LIKE)"
              >
                {{ t('task.like') }}
              </button>
              <ReactionPicker
                :reactions="comment.reactions"
                :disabled="busy"
                @pick="emit('toggle', $event)"
              />
            </template>
            <button
              type="button"
              class="action"
              @click="emit('reply')"
            >
              {{ t('task.reply') }}
            </button>
            <button
              v-if="isMine"
              type="button"
              class="action"
              @click="emit('startEdit')"
            >
              {{ t('common.edit') }}
            </button>
            <button
              v-if="canDelete"
              type="button"
              class="action danger"
              @click="emit('delete')"
            >
              {{ t('team.remove') }}
            </button>
            <time
              class="time"
              :datetime="comment.createdAt"
              :title="fullDate"
            >{{ when }}</time>
            <span
              v-if="comment.editedAt"
              class="time"
            >· {{ t('task.edited') }}</span>
          </div>
        </template>
      </template>
    </div>
  </article>
</template>

<style scoped>
.comment {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.ghost {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
}

.ghost.small {
  width: 24px;
  height: 24px;
}

.main {
  flex: 1;
  min-width: 0;
}

.bubble-wrap {
  position: relative;
  display: inline-block;
  max-width: 100%;
}

.bubble {
  padding: 8px 14px;
  background: var(--color-bubble);
  border-radius: 18px;
}

.bubble.gone {
  display: inline-block;
  background: transparent;
  border: 1px dashed var(--color-border);
  font-size: 13px;
  font-style: italic;
  color: var(--color-text-secondary);
}

.name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.text {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* Tóm tắt biểu cảm gắn ở góc dưới bên phải bong bóng, nhô ra như Facebook */
.reactions {
  position: absolute;
  right: -8px;
  bottom: -12px;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 0 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* Chừa chỗ cho viên tóm tắt biểu cảm đang nhô xuống */
.bubble-wrap:has(.reactions) + .meta {
  margin-top: 16px;
}

.action {
  padding: 0;
  background: none;
  border: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.action:hover:not(:disabled) {
  text-decoration: underline;
}

.action.active {
  color: var(--color-primary);
}

.action.danger:hover {
  color: var(--color-danger);
}

.action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.time {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.edit {
  max-width: 560px;
}

.edit-input {
  display: block;
  width: 100%;
  padding: 8px 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-primary);
  border-radius: 18px;
  font-family: inherit;
  font-size: 14px;
  line-height: 20px;
  resize: vertical;
}

.edit-input:focus {
  outline: 0;
}

.hint {
  margin: 4px 0 0 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.link {
  margin-left: 8px;
  padding: 0;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.link.strong {
  font-weight: 600;
}

.link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

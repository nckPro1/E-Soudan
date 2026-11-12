<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TaskComment } from '../types'
import ReactionBar from './ReactionBar.vue'

/** Một bình luận: nội dung, thao tác (trả lời/sửa/xoá), biểu cảm. Form trả lời do component cha dựng bên dưới. */
const props = defineProps<{
  comment: TaskComment
  isMine: boolean
  canDelete: boolean
  editing: boolean
  busy: boolean
}>()
const emit = defineEmits<{
  reply: []
  startEdit: []
  cancelEdit: []
  saveEdit: [body: string]
  delete: []
  toggle: [emoji: string]
}>()

const MAX = 2000
const { t, locale } = useI18n()
const draft = ref('')

// Mở chế độ sửa thì điền sẵn nội dung hiện tại
watch(
  () => props.editing,
  (value) => {
    if (value) draft.value = props.comment.body ?? ''
  },
  { immediate: true },
)

const dateTime = (iso: string) => new Date(iso).toLocaleString(locale.value)
</script>

<template>
  <article
    class="comment"
    :class="{ gone: comment.deleted }"
  >
    <p
      v-if="comment.deleted"
      class="placeholder"
    >
      {{ t('task.commentDeleted') }}
    </p>

    <template v-else>
      <div class="head">
        <strong>{{ comment.author?.fullName }}</strong>
        <span class="muted">{{ dateTime(comment.createdAt) }}</span>
        <span
          v-if="comment.editedAt"
          class="muted"
          :title="dateTime(comment.editedAt)"
        >· {{ t('task.edited') }}</span>
      </div>

      <form
        v-if="editing"
        class="edit"
        @submit.prevent="emit('saveEdit', draft)"
      >
        <textarea
          v-model="draft"
          class="textarea"
          rows="3"
          :maxlength="MAX"
          :aria-label="t('common.edit')"
          data-testid="edit-input"
        />
        <div class="row">
          <button
            type="button"
            class="btn"
            :disabled="busy"
            @click="emit('cancelEdit')"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="submit"
            class="btn primary"
            :disabled="busy || !draft.trim()"
          >
            {{ t('common.save') }}
          </button>
        </div>
      </form>
      <!-- Văn bản thuần: {{ }} luôn được Vue escape nên không chạy được HTML hay script -->
      <p
        v-else
        class="body"
      >
        {{ comment.body }}
      </p>

      <ReactionBar
        v-if="!editing"
        :reactions="comment.reactions"
        :disabled="busy"
        @toggle="emit('toggle', $event)"
      />

      <div
        v-if="!editing"
        class="actions"
      >
        <button
          type="button"
          class="link"
          @click="emit('reply')"
        >
          {{ t('task.reply') }}
        </button>
        <button
          v-if="isMine"
          type="button"
          class="link"
          @click="emit('startEdit')"
        >
          {{ t('common.edit') }}
        </button>
        <button
          v-if="canDelete"
          type="button"
          class="link danger"
          @click="emit('delete')"
        >
          {{ t('team.remove') }}
        </button>
      </div>
    </template>
  </article>
</template>

<style scoped>
.comment {
  padding: 12px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.comment.gone {
  background: var(--color-surface-muted);
}

.placeholder {
  margin: 0;
  font-size: 13px;
  font-style: italic;
  color: var(--color-text-secondary);
}

.head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
}

.muted {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.body {
  margin: 6px 0 0;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.actions {
  display: flex;
  gap: 16px;
  margin-top: 6px;
}

.link {
  padding: 0;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.link.danger {
  margin-left: auto;
  color: var(--color-danger);
}

.edit {
  margin-top: 6px;
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

.btn {
  height: 32px;
  padding: 0 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
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
</style>

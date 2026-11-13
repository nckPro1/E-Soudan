<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { useProfileStore } from '../../people/profile'
import { timeAgo } from '../../../shared/utils/time'
import type { TaskComment } from '../types'
import { insertAtCaret } from '../../../shared/utils/caret'
import EmojiInsertButton from './EmojiInsertButton.vue'
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
const profile = useProfileStore()
const openAuthor = () => props.comment.author && profile.open(props.comment.author.id)

/** Chèn emoji vào chỗ con trỏ trong ô sửa, rồi đưa con trỏ về ngay sau emoji. */
function insertEmoji(emoji: string) {
  const el = editInput.value
  const result = insertAtCaret(draft.value, el?.selectionStart ?? null, el?.selectionEnd ?? null, emoji, MAX)
  draft.value = result.value
  void nextTick(() => {
    el?.focus()
    el?.setSelectionRange(result.caret, result.caret)
  })
}

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
    class="comment flex items-start gap-2"
    :class="{ small }"
  >
    <button
      v-if="!comment.deleted"
      type="button"
      class="shrink-0 rounded-full"
      :aria-label="authorName"
      @click="openAuthor"
    >
      <UserAvatar
        :name="authorName"
        :size="small ? 24 : 32"
      />
    </button>
    <span
      v-else
      class="ghost h-8 w-8 shrink-0"
      :class="{ small, '!h-6 !w-6': small }"
    />

    <div class="main min-w-0 flex-1">
      <div
        v-if="comment.deleted"
        class="bubble gone inline-block rounded-[1.125rem] border border-dashed border-[#e1dfdd] px-3.5 py-2 text-[0.8125rem] italic text-[#616161]"
      >
        {{ t('task.commentDeleted') }}
      </div>

      <template v-else>
        <form
          v-if="editing"
          class="edit max-w-[35rem]"
          @submit.prevent="draft.trim() && !busy && emit('saveEdit', draft)"
        >
          <textarea
            ref="editInput"
            v-model="draft"
            class="edit-input block w-full resize-y rounded-[1.125rem] border border-[#4f52b2] bg-white px-3.5 py-2 text-sm leading-5 outline-none"
            rows="2"
            :maxlength="MAX"
            :aria-label="t('common.edit')"
            data-testid="edit-input"
            @keydown="onEditKey"
          />
          <p class="hint ml-3 mt-1 flex items-center gap-1 text-xs text-[#616161]">
            <EmojiInsertButton
              :disabled="busy"
              @pick="insertEmoji"
            />
            {{ t('task.editHint') }}
            <button
              type="button"
              class="link ml-2 cursor-pointer border-0 bg-transparent p-0 text-xs text-[#4f52b2]"
              @click="emit('cancelEdit')"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              type="submit"
              class="link strong ml-2 cursor-pointer border-0 bg-transparent p-0 text-xs font-semibold text-[#4f52b2] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="busy || !draft.trim()"
            >
              {{ t('common.save') }}
            </button>
          </p>
        </form>

        <template v-else>
          <div class="bubble-wrap relative inline-block max-w-full">
            <div class="bubble rounded-[1.125rem] bg-[#f0f2f5] px-3.5 py-2">
              <button
                type="button"
                class="name block text-left text-[0.8125rem] font-semibold leading-[1.125rem] hover:underline"
                @click="openAuthor"
              >
                {{ authorName }}
              </button>
              <!-- Văn bản thuần: {{ }} luôn được Vue escape nên không chạy được HTML hay script -->
              <p class="text whitespace-pre-wrap break-words text-sm leading-5">
                {{ comment.body }}
              </p>
            </div>
            <ReactionSummary
              v-if="comment.reactions.length > 0"
              class="reactions absolute -bottom-3 -right-2"
              :reactions="comment.reactions"
            />
          </div>

          <div class="meta ml-3 mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[#616161] [.bubble-wrap:has(.reactions)+&]:mt-4">
            <template v-if="!isMine">
              <button
                type="button"
                class="action cursor-pointer border-0 bg-transparent p-0 text-xs font-semibold text-[#616161] disabled:cursor-not-allowed disabled:opacity-60 hover:underline"
                :class="{ 'active !text-[#4f52b2]': liked }"
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
              class="action cursor-pointer border-0 bg-transparent p-0 text-xs font-semibold text-[#616161] disabled:cursor-not-allowed disabled:opacity-60 hover:underline"
              @click="emit('reply')"
            >
              {{ t('task.reply') }}
            </button>
            <button
              v-if="isMine"
              type="button"
              class="action cursor-pointer border-0 bg-transparent p-0 text-xs font-semibold text-[#616161] disabled:cursor-not-allowed disabled:opacity-60 hover:underline"
              @click="emit('startEdit')"
            >
              {{ t('common.edit') }}
            </button>
            <button
              v-if="canDelete"
              type="button"
              class="action danger cursor-pointer border-0 bg-transparent p-0 text-xs font-semibold text-[#616161] disabled:cursor-not-allowed disabled:opacity-60 hover:text-[#c4314b]"
              @click="emit('delete')"
            >
              {{ t('team.remove') }}
            </button>
            <time
              class="time text-xs font-normal text-[#616161]"
              :datetime="comment.createdAt"
              :title="fullDate"
            >{{ when }}</time>
            <span
              v-if="comment.editedAt"
              class="time text-xs font-normal text-[#616161]"
            >· {{ t('task.edited') }}</span>
          </div>
        </template>
      </template>
    </div>
  </article>
</template>

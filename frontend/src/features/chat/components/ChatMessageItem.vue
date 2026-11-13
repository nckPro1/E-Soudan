<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MenuButton, { type MenuItem } from '../../../shared/components/MenuButton.vue'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { useProfileStore } from '../../people/profile'
import ReactionPicker from '../../task/components/ReactionPicker.vue'
import type { ChatMessage } from '../types'
import ChatAttachments from './ChatAttachments.vue'

/**
 * Một tin nhắn, trình bày như Teams: tin của người khác nằm bên trái (kèm ảnh đại diện và tên ở tin đầu của cụm), tin của
 * mình nằm bên phải với bong bóng tím nhạt. Rê chuột vào tin thì hiện thanh nhỏ (trả lời, thả biểu cảm, menu sửa/xoá của
 * mình). Biểu cảm hiện thành viên thuốc trắng ngay dưới bong bóng. Tin đã xoá chỉ còn dòng "Tin nhắn đã bị xoá".
 * Nội dung luôn hiển thị là văn bản thuần (Vue tự escape), không bao giờ dựng HTML từ tin nhắn.
 */
const props = defineProps<{
  message: ChatMessage
  mine: boolean
  /** Tin đầu của một cụm cùng người gửi: hiện ảnh đại diện và tên */
  showHeader: boolean
  /** Còn gửi/sửa/xoá/thả biểu cảm được không (không phải cuộc trò chuyện chỉ để xem) */
  canSend: boolean
}>()
const emit = defineEmits<{
  reply: []
  saveEdit: [body: string]
  delete: []
  react: [emoji: string]
}>()

const { t, locale } = useI18n()
const profile = useProfileStore()

const editing = ref(false)
const draft = ref('')
const editInput = ref<HTMLTextAreaElement | null>(null)

const authorName = computed(() => props.message.author?.fullName ?? '')
const time = computed(() =>
  new Date(props.message.createdAt).toLocaleTimeString(locale.value, { hour: '2-digit', minute: '2-digit' }),
)
const fullDate = computed(() => new Date(props.message.createdAt).toLocaleString(locale.value))

const menuItems = computed<MenuItem[]>(() => [
  { key: 'edit', label: t('common.edit') },
  { key: 'delete', label: t('chat.deleteMessage'), danger: true },
])

async function startEdit() {
  draft.value = props.message.body ?? ''
  editing.value = true
  await nextTick()
  editInput.value?.focus()
  editInput.value?.setSelectionRange(draft.value.length, draft.value.length)
}

function onMenu(key: string) {
  if (key === 'edit') void startEdit()
  else if (key === 'delete') emit('delete')
}

function saveEdit() {
  const body = draft.value.trim()
  if (!body) return
  editing.value = false
  if (body !== props.message.body) emit('saveEdit', body)
}

function onEditKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    saveEdit()
  } else if (event.key === 'Escape') {
    editing.value = false
  }
}

function openProfile() {
  if (props.message.author) profile.open(props.message.author.id)
}
</script>

<template>
  <div
    class="chat-message group flex gap-3 px-6 max-md:px-3"
    :class="[mine ? 'justify-end' : '', showHeader ? 'mt-3' : 'mt-0.5']"
    :data-testid="mine ? 'message-mine' : 'message-other'"
  >
    <!-- Ảnh đại diện (tin của người khác); các tin sau trong cụm chỉ chừa chỗ -->
    <div
      v-if="!mine"
      class="w-8 shrink-0"
    >
      <button
        v-if="showHeader && message.author"
        type="button"
        class="rounded-full"
        :aria-label="authorName"
        @click="openProfile"
      >
        <UserAvatar
          :name="authorName"
          :size="32"
        />
      </button>
    </div>

    <div
      class="min-w-0 max-w-[70%] max-md:max-w-[85%]"
      :class="mine ? 'items-end' : ''"
    >
      <div
        v-if="showHeader"
        class="mb-1 flex items-baseline gap-2 text-xs text-[#616161]"
        :class="mine ? 'justify-end' : ''"
      >
        <button
          v-if="!mine"
          type="button"
          class="font-semibold hover:underline"
          @click="openProfile"
        >
          {{ authorName }}
        </button>
        <time
          :datetime="message.createdAt"
          :title="fullDate"
          class="text-[0.6875rem]"
        >{{ time }}</time>
      </div>

      <div class="relative">
        <!-- Tin đã xoá -->
        <div
          v-if="message.deleted"
          class="inline-block rounded-2xl border border-dashed border-[#e1dfdd] px-3 py-2 text-[0.8125rem] italic text-[#616161]"
          data-testid="message-deleted"
        >
          {{ t('chat.messageDeleted') }}
        </div>

        <!-- Đang sửa -->
        <form
          v-else-if="editing"
          class="w-[28rem] max-w-full"
          @submit.prevent="saveEdit"
        >
          <textarea
            ref="editInput"
            v-model="draft"
            rows="2"
            maxlength="4000"
            class="w-full resize-y rounded-lg border border-[#4f52b2] bg-white px-3 py-2 text-[0.8125rem] outline-none"
            :aria-label="t('common.edit')"
            data-testid="chat-edit-input"
            @keydown="onEditKey"
          />
          <div class="mt-1 flex justify-end gap-2 text-xs">
            <button
              type="button"
              class="btn-link !text-[#616161]"
              @click="editing = false"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              type="submit"
              class="btn-link"
              :disabled="!draft.trim()"
            >
              {{ t('common.save') }}
            </button>
          </div>
        </form>

        <!-- Bình thường -->
        <template v-else>
          <div
            class="inline-block max-w-full rounded-2xl px-3.5 py-2 text-[0.8125rem] leading-relaxed text-[#242424]"
            :class="mine ? 'rounded-tr-sm bg-[#e8ebfa]' : 'rounded-tl-sm bg-[#f0f2f5]'"
          >
            <!-- Trích dẫn tin được trả lời -->
            <div
              v-if="message.replyTo"
              class="mb-1 border-l-2 border-[#4f52b2] pl-2 text-xs text-[#616161]"
              data-testid="message-reply"
            >
              <span class="font-semibold">{{ message.replyTo.author?.fullName ?? '' }}</span>
              <span
                v-if="message.replyTo.deleted"
                class="block italic"
              >{{ t('chat.messageDeleted') }}</span>
              <span
                v-else
                class="block truncate"
              >{{ message.replyTo.snippet ?? t('chat.attachment') }}</span>
            </div>
            <p
              v-if="message.body"
              class="whitespace-pre-wrap break-words"
              data-testid="message-body"
            >
              {{ message.body }}
            </p>
            <span
              v-if="message.editedAt"
              class="ml-1 text-[0.6875rem] text-[#616161]"
            >({{ t('chat.edited') }})</span>
          </div>

          <ChatAttachments
            v-if="message.attachments.length > 0"
            :message-id="message.id"
            :attachments="message.attachments"
          />

          <!-- Thanh thao tác khi rê chuột -->
          <div
            v-if="canSend"
            class="absolute -top-3.5 flex items-center gap-0.5 rounded-md border border-[#e1dfdd] bg-white px-0.5 py-0.5 opacity-0 shadow-sm transition-opacity focus-within:opacity-100 group-hover:opacity-100"
            :class="mine ? 'right-0' : 'left-0'"
            data-testid="message-toolbar"
          >
            <button
              type="button"
              class="flex h-6 w-6 items-center justify-center rounded text-[#616161] hover:bg-[#f5f5f5] hover:text-[#242424]"
              :title="t('chat.reply')"
              :aria-label="t('chat.reply')"
              data-testid="message-reply-button"
              @click="emit('reply')"
            >
              <svg
                class="h-3.5 w-3.5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              ><path d="M9 14L4 9l5-5M4 9h10a6 6 0 016 6v3" /></svg>
            </button>
            <ReactionPicker
              v-if="!mine"
              plus
              :reactions="message.reactions"
              @pick="emit('react', $event)"
            />
            <MenuButton
              v-if="mine"
              :items="menuItems"
              :label="t('report.moreActions')"
              @select="onMenu"
            />
          </div>
        </template>
      </div>

      <!-- Biểu cảm -->
      <div
        v-if="!message.deleted && message.reactions.length > 0"
        class="mt-1 flex w-fit items-center space-x-1.5 rounded-full border border-[#e1dfdd] bg-white px-2 py-0.5 text-xs shadow-sm"
        :class="mine ? 'ml-auto' : ''"
        data-testid="message-reactions"
      >
        <button
          v-for="r in message.reactions"
          :key="r.emoji"
          type="button"
          class="flex items-center rounded-full hover:bg-gray-50 disabled:cursor-default"
          :class="{ 'font-semibold text-[#4f52b2]': r.reactedByMe }"
          :disabled="mine || !canSend"
          :title="r.users.join(', ')"
          :aria-pressed="r.reactedByMe"
          @click="emit('react', r.emoji)"
        >
          <span class="mr-1 text-sm">{{ r.emoji }}</span>{{ r.count }}
        </button>
      </div>
    </div>
  </div>
</template>

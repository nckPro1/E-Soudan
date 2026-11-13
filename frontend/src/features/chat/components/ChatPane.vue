<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import { extensionOf, ALLOWED_EXTENSIONS, MAX_FILE_BYTES } from '../../../shared/utils/fileSize'
import { useAuthStore } from '../../auth/store'
import { useChatStore } from '../store'
import type { ChatMessage, Conversation } from '../types'
import ChatComposer from './ChatComposer.vue'
import ChatMessageItem from './ChatMessageItem.vue'

/**
 * Khung trò chuyện: danh sách tin (cũ ở trên, mới ở dưới, ngăn theo ngày, gom tin liên tiếp của một người), ô soạn tin.
 * Dùng cho cả nhắn riêng lẫn phòng chat của nhóm. Mở ra thì cuộn xuống tin mới nhất; cuộn lên đầu thì tải thêm tin cũ
 * mà vẫn giữ nguyên vị trí đang đọc. Có tin mới khi đang đọc ở trên cao thì hiện nút "tin mới" thay vì kéo xuống.
 */
const props = defineProps<{ conversation: Conversation }>()

const { t, te, locale } = useI18n()
const chat = useChatStore()
const auth = useAuthStore()

const scroller = ref<HTMLElement | null>(null)
const composer = ref<InstanceType<typeof ChatComposer> | null>(null)
const replyTo = ref<ChatMessage | null>(null)
const sending = ref(false)
const errorText = ref('')
const showNewPill = ref(false)

const cache = computed(() => chat.peek(props.conversation.id))
const items = computed(() => cache.value.items)
const myId = computed(() => auth.user?.id ?? null)

/** Khoảng cách (px) tới đáy mà vẫn coi là "đang ở cuối". */
const NEAR_BOTTOM = 80
const nearBottom = () => {
  const el = scroller.value
  return !el || el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM
}

const dayKey = (iso: string) => new Date(iso).toDateString()

function dayLabel(iso: string): string {
  const date = new Date(iso)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return t('chat.today')
  if (date.toDateString() === yesterday.toDateString()) return t('chat.yesterday')
  return date.toLocaleDateString(locale.value, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
}

/** Mỗi tin kèm: có cần vạch ngày trước nó không, và có phải tin đầu của cụm (cùng người, liền nhau, dưới 5 phút). */
const rows = computed(() =>
  items.value.map((message, index) => {
    const prev = items.value[index - 1]
    const newDay = !prev || dayKey(prev.createdAt) !== dayKey(message.createdAt)
    const sameAuthor = !!prev && prev.author?.id === message.author?.id
    const close = !!prev && Date.parse(message.createdAt) - Date.parse(prev.createdAt) < 5 * 60 * 1000
    return {
      message,
      dayLabel: newDay ? dayLabel(message.createdAt) : null,
      showHeader: newDay || !sameAuthor || !close || !!message.replyTo,
    }
  }),
)

function scrollToBottom() {
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
  showNewPill.value = false
}

// Mở cuộc trò chuyện: tải trang mới nhất, đánh dấu đã đọc, cuộn xuống đáy
watch(
  () => props.conversation.id,
  async (id, oldId) => {
    if (oldId !== undefined) chat.close(oldId)
    replyTo.value = null
    errorText.value = ''
    showNewPill.value = false
    try {
      await chat.open(id)
    } catch (error) {
      errorText.value = errorOf(toApiError(error).code)
    }
    await nextTick()
    scrollToBottom()
  },
  { immediate: true },
)
onBeforeUnmount(() => chat.close(props.conversation.id))

// Tin mới ở cuối: đang ở đáy thì tự cuộn; đang đọc ở trên thì chỉ báo
watch(
  () => items.value[items.value.length - 1]?.id,
  async (id, oldId) => {
    if (oldId === undefined || id === undefined) return
    const last = items.value[items.value.length - 1]
    const wasNear = nearBottom() || last.author?.id === myId.value
    await nextTick()
    if (wasNear) scrollToBottom()
    else showNewPill.value = true
  },
)

// Tải thêm tin cũ ở đầu: giữ nguyên vị trí đang đọc bằng cách bù phần chiều cao vừa thêm
async function loadOlder() {
  const el = scroller.value
  if (!el || cache.value.loading || !cache.value.hasMore) return
  const before = el.scrollHeight
  try {
    await chat.loadOlder(props.conversation.id)
  } catch (error) {
    errorText.value = errorOf(toApiError(error).code)
    return
  }
  await nextTick()
  el.scrollTop = el.scrollHeight - before
}

function onScroll() {
  const el = scroller.value
  if (!el) return
  if (el.scrollTop < 60) void loadOlder()
  if (nearBottom()) {
    showNewPill.value = false
    void chat.markRead(props.conversation.id)
  }
}

function errorOf(code: string): string {
  return te(`errors.${code}`) ? t(`errors.${code}`) : t('errors.UNKNOWN')
}

/** Kiểm tra file trước khi gửi: đúng định dạng cho phép và không quá 10 MB. */
function invalidFile(files: File[]): string | null {
  for (const file of files) {
    if (!ALLOWED_EXTENSIONS.includes(extensionOf(file.name))) return t('chat.fileType', { name: file.name })
    if (file.size > MAX_FILE_BYTES) return t('chat.fileTooBig', { name: file.name })
  }
  return null
}

async function send(payload: { body: string; files: File[] }) {
  if (sending.value) return
  const problem = invalidFile(payload.files)
  if (problem) {
    errorText.value = problem
    return
  }
  sending.value = true
  errorText.value = ''
  try {
    await chat.send(props.conversation.id, {
      body: payload.body,
      replyToId: replyTo.value?.id ?? null,
      files: payload.files,
    })
    replyTo.value = null
    composer.value?.clear()
    await nextTick()
    scrollToBottom()
  } catch (error) {
    errorText.value = errorOf(toApiError(error).code)
  } finally {
    sending.value = false
  }
}

async function guard(action: () => Promise<unknown>) {
  errorText.value = ''
  try {
    await action()
  } catch (error) {
    errorText.value = errorOf(toApiError(error).code)
  }
}

const saveEdit = (message: ChatMessage, body: string) => guard(() => chat.edit(message.id, body))
const remove = (message: ChatMessage) => guard(() => chat.remove(message.id))
const react = (message: ChatMessage, emoji: string) => guard(() => chat.toggleReaction(message, emoji))

const readOnlyText = computed(() => {
  if (props.conversation.canSend) return ''
  return props.conversation.type === 'TEAM' && props.conversation.team && !props.conversation.team.active
    ? t('chat.readOnlyArchived')
    : t('chat.readOnly')
})
</script>

<template>
  <div class="chat-pane relative flex h-full min-h-0 flex-col bg-white">
    <div
      ref="scroller"
      class="min-h-0 flex-1 overflow-y-auto py-3"
      data-testid="chat-scroller"
      @scroll.passive="onScroll"
    >
      <div
        v-if="cache.hasMore"
        class="flex justify-center py-2"
      >
        <button
          type="button"
          class="btn-link text-xs"
          :disabled="cache.loading"
          data-testid="chat-load-older"
          @click="loadOlder"
        >
          {{ cache.loading ? t('common.loading') : t('chat.loadOlder') }}
        </button>
      </div>

      <p
        v-if="cache.loaded && items.length === 0"
        class="px-6 py-10 text-center text-[0.8125rem] text-[#616161]"
        data-testid="chat-no-messages"
      >
        {{ t('chat.noMessages') }}
      </p>
      <p
        v-else-if="!cache.loaded && cache.loading"
        class="px-6 py-10 text-center text-[0.8125rem] text-[#616161]"
      >
        {{ t('common.loading') }}
      </p>

      <template
        v-for="row in rows"
        :key="row.message.id"
      >
        <div
          v-if="row.dayLabel"
          class="my-3 flex items-center gap-3 px-6 text-[0.6875rem] text-[#616161]"
          role="separator"
        >
          <span class="h-px flex-1 bg-[#e1dfdd]" />
          <span>{{ row.dayLabel }}</span>
          <span class="h-px flex-1 bg-[#e1dfdd]" />
        </div>
        <ChatMessageItem
          :message="row.message"
          :mine="row.message.author?.id === myId"
          :show-header="row.showHeader"
          :can-send="conversation.canSend"
          @reply="replyTo = row.message"
          @save-edit="saveEdit(row.message, $event)"
          @delete="remove(row.message)"
          @react="react(row.message, $event)"
        />
      </template>
    </div>

    <button
      v-if="showNewPill"
      type="button"
      class="absolute bottom-24 left-1/2 -translate-x-1/2 rounded-full bg-[#4f52b2] px-3 py-1 text-xs font-semibold text-white shadow-md"
      data-testid="chat-new-pill"
      @click="scrollToBottom"
    >
      ↓ {{ t('chat.newMessageIn') }}
    </button>

    <AlertBanner
      v-if="errorText"
      class="mx-6 mb-2"
    >
      {{ errorText }}
    </AlertBanner>

    <ChatComposer
      v-if="conversation.canSend"
      ref="composer"
      :busy="sending"
      :reply-to="replyTo"
      @send="send"
      @cancel-reply="replyTo = null"
    />
    <p
      v-else
      class="border-t border-[#e1dfdd] bg-[#f5f5f5] px-6 py-3 text-center text-xs text-[#616161]"
      data-testid="chat-read-only"
    >
      {{ readOnlyText }}
    </p>
  </div>
</template>

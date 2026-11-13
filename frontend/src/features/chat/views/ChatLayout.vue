<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { timeAgo } from '../../../shared/utils/time'
import * as peopleApi from '../../people/api'
import { useProfileStore } from '../../people/profile'
import type { Person } from '../../people/types'
import { useAuthStore } from '../../auth/store'
import { useChatStore } from '../store'
import type { Conversation } from '../types'

/**
 * App Trò chuyện, bố cục như Teams: cột trái là danh sách cuộc nhắn riêng (lời mời đang chờ nằm trên cùng, có chip lọc
 * Chưa đọc / Lời mời, ô tìm), khung phải là cuộc trò chuyện đang chọn. Nhắn nhóm không nằm ở đây mà ở mục Nhóm.
 * Muốn nhắn người mới thì tìm người ở hộp "Cuộc trò chuyện mới" rồi mở hồ sơ để gửi lời mời.
 */
const { t, locale } = useI18n()
const route = useRoute()
const chat = useChatStore()
const auth = useAuthStore()
const profile = useProfileStore()

const query = ref('')
const chip = ref<'unread' | 'requests' | null>(null)
const picking = ref(false)

const selectedId = computed(() => (route.params.id ? Number(route.params.id) : null))
const otherName = (c: Conversation) => c.other?.fullName ?? ''

const incoming = computed(() => chat.incomingRequests)
const visible = computed(() => {
  const text = query.value.trim().toLowerCase()
  return chat.conversations.filter((c) => {
    if (c.status === 'PENDING' && !c.requestedByMe) return false // lời mời đến có mục riêng
    if (text && !otherName(c).toLowerCase().includes(text)) return false
    if (chip.value === 'unread') return (chat.unreadByConversation[c.id] ?? 0) > 0
    if (chip.value === 'requests') return false
    return true
  })
})
const showRequests = computed(() => chip.value !== 'unread' && incoming.value.length > 0)

function toggleChip(value: 'unread' | 'requests') {
  chip.value = chip.value === value ? null : value
}

function preview(c: Conversation): string {
  if (c.status === 'PENDING') return c.requestedByMe ? t('chat.requestSentTitle') : t('chat.requestIncomingTitle', { name: otherName(c) })
  const last = c.last
  if (!last) return ''
  if (last.deleted) return t('chat.messageDeleted')
  const text = last.snippet ?? (last.hasAttachment ? t('chat.attachment') : '')
  return last.authorId === auth.user?.id ? `${t('chat.you')}: ${text}` : text
}

function when(c: Conversation): string {
  const iso = c.last?.at ?? c.createdAt
  return timeAgo(iso, locale.value) ?? t('task.justNow')
}

// ---- hộp "Cuộc trò chuyện mới": tìm người rồi mở hồ sơ ----
const people = ref<Person[]>([])
const peopleQuery = ref('')
const peopleLoading = ref(false)
let seq = 0
let timer: ReturnType<typeof setTimeout> | undefined

async function searchPeople() {
  const mine = ++seq
  peopleLoading.value = true
  try {
    const found = await peopleApi.searchPeople(peopleQuery.value.trim(), 20)
    if (mine === seq) people.value = found.filter((p) => p.id !== auth.user?.id)
  } catch {
    if (mine === seq) people.value = []
  } finally {
    if (mine === seq) peopleLoading.value = false
  }
}

watch(peopleQuery, () => {
  clearTimeout(timer)
  timer = setTimeout(() => void searchPeople(), 250)
})
watch(picking, (open) => {
  if (open) {
    peopleQuery.value = ''
    people.value = []
    void searchPeople()
  }
})
onBeforeUnmount(() => clearTimeout(timer))

function pickPerson(person: Person) {
  picking.value = false
  profile.open(person.id)
}
</script>

<template>
  <div class="flex h-full">
    <aside
      class="flex w-[17.5rem] shrink-0 flex-col border-r border-[#e0e0e0] bg-[#f5f5f5] max-md:w-full max-md:border-r-0"
      :class="{ 'max-md:hidden': selectedId !== null }"
      :aria-label="t('chat.title')"
      data-purpose="chat-sidebar"
    >
      <div class="flex items-center justify-between px-4 pb-2 pt-3">
        <h1 class="text-xl font-bold text-[#242424]">
          {{ t('chat.title') }}
        </h1>
        <button
          type="button"
          class="rounded p-1 text-[#616161] hover:bg-black/5"
          :title="t('chat.newChat')"
          :aria-label="t('chat.newChat')"
          data-testid="new-chat"
          @click="picking = true"
        >
          <svg
            class="h-[1.125rem] w-[1.125rem]"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path d="M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z" /></svg>
        </button>
      </div>

      <div class="px-3 pb-2">
        <input
          v-model="query"
          type="search"
          class="h-8 w-full rounded-md border border-transparent bg-white px-2.5 text-xs outline-none placeholder:text-[#707070] focus:border-[#4f52b2]"
          :placeholder="t('chat.searchChats')"
          :aria-label="t('chat.searchChats')"
          data-testid="chat-search"
        >
      </div>

      <div class="flex gap-1.5 px-3 pb-2 text-xs">
        <button
          type="button"
          class="rounded-full border px-2.5 py-0.5"
          :class="chip === 'unread' ? 'border-[#4f52b2] bg-[#4f52b2] text-white' : 'border-[#d1d1d1] bg-white text-[#424242] hover:bg-[#edebe9]'"
          :aria-pressed="chip === 'unread'"
          @click="toggleChip('unread')"
        >
          {{ t('chat.filterUnread') }}
        </button>
        <button
          type="button"
          class="rounded-full border px-2.5 py-0.5"
          :class="chip === 'requests' ? 'border-[#4f52b2] bg-[#4f52b2] text-white' : 'border-[#d1d1d1] bg-white text-[#424242] hover:bg-[#edebe9]'"
          :aria-pressed="chip === 'requests'"
          @click="toggleChip('requests')"
        >
          {{ t('chat.filterRequests') }}
        </button>
      </div>

      <div
        class="min-h-0 flex-1 overflow-y-auto px-2 pb-3"
        data-testid="conversation-list"
      >
        <!-- Lời mời đang chờ mình trả lời -->
        <section
          v-if="showRequests || chip === 'requests'"
          class="mb-2"
        >
          <h2
            v-if="incoming.length > 0"
            class="px-2 pb-1 pt-1 text-[0.6875rem] font-semibold uppercase tracking-wide text-[#616161]"
          >
            {{ t('chat.requests') }} ({{ incoming.length }})
          </h2>
          <RouterLink
            v-for="c in incoming"
            :key="c.id"
            :to="{ name: 'chat-conversation', params: { id: c.id } }"
            class="flex items-center gap-2.5 rounded-lg px-2 py-2"
            :class="selectedId === c.id ? 'bg-white shadow-sm ring-1 ring-[#e1dfdd]' : 'hover:bg-[#edebe9]'"
            data-testid="chat-request-item"
          >
            <UserAvatar
              :name="otherName(c)"
              :size="36"
            />
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[0.8125rem] font-semibold">{{ otherName(c) }}</span>
              <span class="block truncate text-xs text-[#4f52b2]">{{ t('chat.requestIncomingTitle', { name: otherName(c) }) }}</span>
            </span>
          </RouterLink>
        </section>

        <RouterLink
          v-for="c in visible"
          :key="c.id"
          :to="{ name: 'chat-conversation', params: { id: c.id } }"
          class="flex items-center gap-2.5 rounded-lg px-2 py-2"
          :class="selectedId === c.id ? 'bg-white shadow-sm ring-1 ring-[#e1dfdd]' : 'hover:bg-[#edebe9]'"
          :aria-current="selectedId === c.id ? 'page' : undefined"
          data-testid="chat-item"
        >
          <UserAvatar
            :name="otherName(c)"
            :size="36"
          />
          <span class="min-w-0 flex-1">
            <span class="flex items-baseline justify-between gap-2">
              <span
                class="truncate text-[0.8125rem]"
                :class="(chat.unreadByConversation[c.id] ?? 0) > 0 ? 'font-bold' : 'font-semibold'"
              >{{ otherName(c) }}</span>
              <span class="shrink-0 text-[0.6875rem] text-[#616161]">{{ when(c) }}</span>
            </span>
            <span class="flex items-center justify-between gap-2">
              <span
                class="truncate text-xs"
                :class="(chat.unreadByConversation[c.id] ?? 0) > 0 ? 'font-semibold text-[#242424]' : 'text-[#616161]'"
              >{{ preview(c) }}</span>
              <span
                v-if="(chat.unreadByConversation[c.id] ?? 0) > 0"
                class="flex h-4 min-w-[1rem] shrink-0 items-center justify-center rounded-full bg-[#c4314b] px-1 text-[0.625rem] font-bold leading-none text-white"
                data-testid="chat-item-unread"
              >{{ chat.unreadByConversation[c.id] }}</span>
            </span>
          </span>
        </RouterLink>

        <p
          v-if="chat.conversationsLoaded && visible.length === 0 && incoming.length === 0"
          class="px-3 py-6 text-center text-xs text-[#616161]"
          data-testid="chat-list-empty"
        >
          {{ t('chat.empty') }}
        </p>
      </div>
    </aside>

    <section
      class="min-w-0 flex-1 overflow-hidden bg-white"
      :class="{ 'max-md:hidden': selectedId === null }"
    >
      <RouterView :key="route.fullPath" />
    </section>

    <ModalDialog
      v-if="picking"
      :title="t('chat.newChat')"
      @close="picking = false"
    >
      <input
        v-model="peopleQuery"
        type="search"
        class="control mb-3 w-full"
        :placeholder="t('chat.searchPeople')"
        :aria-label="t('chat.searchPeople')"
        data-testid="people-search"
        autofocus
      >
      <ul
        class="max-h-72 overflow-y-auto"
        data-testid="people-results"
      >
        <li
          v-for="p in people"
          :key="p.id"
        >
          <button
            type="button"
            class="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-[#f5f5f5]"
            data-testid="people-result"
            @click="pickPerson(p)"
          >
            <UserAvatar
              :name="p.fullName"
              :size="32"
            />
            <span class="min-w-0">
              <span class="block truncate text-[0.8125rem] font-semibold">{{ p.fullName }}</span>
              <span class="block truncate text-xs text-[#616161]">{{ p.position ?? p.email }}</span>
            </span>
          </button>
        </li>
        <li
          v-if="!peopleLoading && people.length === 0"
          class="px-2 py-4 text-center text-xs text-[#616161]"
        >
          {{ t('chat.noPeople') }}
        </li>
      </ul>
    </ModalDialog>
  </div>
</template>

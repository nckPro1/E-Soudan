<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import * as notificationApi from '../api'
import NotificationItem from '../components/NotificationItem.vue'
import { useNotificationStore } from '../store'
import type { AppNotification } from '../types'

/**
 * Mục Hoạt động: toàn bộ thông báo gửi cho mình (task, bình luận, biểu cảm, báo cáo), mới nhất trước. Có bộ lọc "Chưa
 * đọc", nút "Đánh dấu tất cả đã đọc". Bấm vào một dòng thì đánh dấu đã đọc và mở đúng task hoặc trang báo cáo.
 */
const PAGE_SIZE = 20

const { t, te } = useI18n()
const router = useRouter()
const store = useNotificationStore()

const items = ref<AppNotification[]>([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const errorCode = ref('')
const unreadOnly = ref(false)

const hasMore = computed(() => page.value + 1 < totalPages.value)
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function load() {
  errorCode.value = ''
  try {
    const result = await notificationApi.listNotifications(unreadOnly.value, 0, PAGE_SIZE)
    items.value = result.items
    page.value = result.page
    totalPages.value = result.totalPages
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    loading.value = false
  }
}

async function more() {
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    const result = await notificationApi.listNotifications(unreadOnly.value, page.value + 1, PAGE_SIZE)
    items.value = [...items.value, ...result.items]
    page.value = result.page
    totalPages.value = result.totalPages
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  void load()
  void store.refresh()
})

// Có thông báo mới (số chưa đọc đổi do lần hỏi định kỳ) thì nạp lại danh sách từ đầu
watch(
  () => store.unread,
  () => void load(),
)

function setFilter(value: boolean) {
  if (unreadOnly.value === value) return
  unreadOnly.value = value
  loading.value = true
  void load()
}

/** Đường dẫn khi bấm vào một thông báo. */
function target(n: AppNotification): RouteLocationRaw | null {
  if (n.type === 'REPORT_REMINDER' && n.communityId !== null) {
    return { name: 'community-feed', params: { id: n.communityId } }
  }
  if (n.type.startsWith('TASK') && n.taskId !== null) {
    return { name: 'task-detail', params: { id: n.taskId } }
  }
  if (n.type.startsWith('REPORT') && n.communityId !== null && n.postAuthorId !== null) {
    return { name: 'member-reports', params: { id: n.communityId, userId: n.postAuthorId } }
  }
  if (n.type.startsWith('CHAT') && n.conversationId !== null) {
    return { name: 'chat-conversation', params: { id: n.conversationId } }
  }
  return null
}

async function open(n: AppNotification) {
  if (!n.read) {
    try {
      const updated = await notificationApi.markRead(n.id)
      items.value = items.value.map((x) => (x.id === n.id ? updated : x))
      store.set(store.unread - 1)
    } catch (error) {
      errorCode.value = toApiError(error).code
    }
  }
  const to = target(n)
  if (to) await router.push(to)
}

async function readAll() {
  try {
    await notificationApi.markAllRead()
    store.set(0)
    items.value = unreadOnly.value ? [] : items.value.map((x) => ({ ...x, read: true }))
  } catch (error) {
    errorCode.value = toApiError(error).code
  }
}
</script>

<template>
  <section
    class="activity mx-auto flex max-w-[48rem] flex-col"
    data-testid="activity"
  >
    <header class="flex flex-wrap items-center justify-between gap-3 pb-3">
      <h1 class="text-xl font-bold text-[#242424]">
        {{ t('notif.title') }}
      </h1>
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex h-7 w-7 items-center justify-center rounded border border-[#d1d1d1] bg-white text-[#616161] hover:bg-[#f5f5f5]"
          :class="store.soundOn ? '!text-[#4f52b2]' : ''"
          :title="store.soundOn ? t('notif.soundOn') : t('notif.soundOff')"
          :aria-label="t('notif.soundLabel')"
          :aria-pressed="store.soundOn"
          data-testid="toggle-sound"
          @click="store.toggleSound()"
        >
          <svg
            class="h-4 w-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path :d="store.soundOn ? 'M11 5L6 9H3v6h3l5 4zM15.5 8.5a5 5 0 010 7M18.5 5.5a9 9 0 010 13' : 'M11 5L6 9H3v6h3l5 4zM16 9l5 6M21 9l-5 6'" /></svg>
        </button>
        <button
          type="button"
          class="btn btn-sm"
          :disabled="store.unread === 0"
          data-testid="read-all"
          @click="readAll"
        >
          {{ t('notif.readAll') }}
        </button>
      </div>
    </header>

    <div class="flex items-center gap-1.5 pb-3 text-xs">
      <button
        type="button"
        class="rounded-full border px-2.5 py-1"
        :class="!unreadOnly ? 'border-[#4f52b2] bg-[#4f52b2]/10 text-[#4f52b2]' : 'border-[#e1dfdd] bg-white text-[#424242] hover:bg-black/5'"
        :aria-pressed="!unreadOnly"
        data-testid="filter-all"
        @click="setFilter(false)"
      >
        {{ t('notif.all') }}
      </button>
      <button
        type="button"
        class="rounded-full border px-2.5 py-1"
        :class="unreadOnly ? 'border-[#4f52b2] bg-[#4f52b2]/10 text-[#4f52b2]' : 'border-[#e1dfdd] bg-white text-[#424242] hover:bg-black/5'"
        :aria-pressed="unreadOnly"
        data-testid="filter-unread"
        @click="setFilter(true)"
      >
        {{ t('notif.unreadOnly') }}<template v-if="store.unread > 0">
          ({{ store.unread }})
        </template>
      </button>
    </div>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <p
      v-if="loading"
      class="py-8 text-center text-xs text-[#616161]"
    >
      {{ t('common.loading') }}
    </p>
    <p
      v-else-if="items.length === 0 && !errorMessage"
      class="py-10 text-center text-[0.8125rem] text-[#616161]"
      data-testid="empty"
    >
      {{ unreadOnly ? t('notif.emptyUnread') : t('notif.empty') }}
    </p>

    <div
      v-if="items.length > 0"
      class="overflow-hidden rounded-lg border border-[#e1dfdd] bg-white"
      data-testid="notification-list"
    >
      <NotificationItem
        v-for="n in items"
        :key="n.id"
        :notification="n"
        @open="open(n)"
      />
    </div>

    <div
      v-if="hasMore"
      class="flex justify-center pt-4"
    >
      <button
        type="button"
        class="btn"
        :disabled="loadingMore"
        @click="more"
      >
        {{ loadingMore ? t('common.loading') : t('report.loadMore') }}
      </button>
    </div>
  </section>
</template>

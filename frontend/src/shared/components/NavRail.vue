<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '../../features/chat/store'
import { useNotificationStore } from '../../features/notification/store'
import { usePermission } from '../composables/usePermission'

/**
 * Thanh ứng dụng dọc bên trái. Markup lấy nguyên từ bản thiết kế Stitch (design/homepage-assets/teams.html,
 * khối "LeftAppRail"): chỉ icon, mục đang chọn có vạch tím bên trái và nền tím nhạt. App chưa làm (soon) vẫn có
 * chỗ để cấu trúc điều hướng ổn định; bấm vào thấy trang "Sắp có". Mục menu chỉ là tiện lợi, quyền thật ở backend.
 */
const { t } = useI18n()
const route = useRoute()
const { can, isAdmin } = usePermission()
const notifications = useNotificationStore()
const chat = useChatStore()

/** Số chưa đọc hiện trên icon của app: Hoạt động (thông báo) và Trò chuyện (tin nhắn). */
function badgeOf(key: string): number {
  if (key === 'activity') return notifications.unread
  if (key === 'chat') return chat.totalUnread
  return 0
}

/** Nhãn đọc được cho trình đọc màn hình: tên app, kèm số chưa đọc nếu là Hoạt động. */
function labelOf(item: { key: string; label: string }): string {
  const count = badgeOf(item.key)
  return count > 0 ? `${item.label} (${count})` : item.label
}

// Đường vẽ icon (viewBox 24, nét 1.8). Mỗi app một icon.
const ICONS: Record<string, string> = {
  activity: 'M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 00-4-5.7V5a2 2 0 10-4 0v.3C7.7 6.2 6 8.4 6 11v3.2c0 .5-.2 1-.6 1.4L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9',
  chat: 'M4 5h16v11H9l-5 4z',
  communities:
    'M17 20h5v-2a3 3 0 00-5.4-1.9M17 20H7m10 0v-2c0-.7-.1-1.3-.4-1.9M7 20H2v-2a3 3 0 015.4-1.9M7 20v-2c0-.7.1-1.3.4-1.9m0 0a5 5 0 019.3 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z',
  tasks: 'M5 7l1.5 1.5L9 6M5 13l1.5 1.5L9 12M5 19l1.5 1.5L9 18M12 7h8M12 13h8M12 19h8',
  teams: 'M4 8h16v11H4zM9 8V6h6v2M4 13h16',
  calendar: 'M5 4h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2zM16 2v4M8 2v4M3 10h18',
  ai: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z',
  people: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z',
}

interface RailItem {
  key: string
  route: string
  path: string
  label: string
  visible: boolean
  soon: boolean
}

const items = computed<RailItem[]>(() =>
  [
    { key: 'activity', route: 'activity', path: '/activity', label: t('nav.activity'), visible: !isAdmin.value, soon: true },
    { key: 'chat', route: 'chat', path: '/chat', label: t('nav.chat'), visible: !isAdmin.value, soon: false },
    { key: 'communities', route: 'communities', path: '/communities', label: t('nav.communities'), visible: !isAdmin.value, soon: false },
    { key: 'tasks', route: 'my-tasks', path: '/tasks', label: t('nav.tasks'), visible: !isAdmin.value, soon: false },
    { key: 'teams', route: 'teams', path: '/teams', label: t('nav.teams'), visible: true, soon: false },
    { key: 'calendar', route: 'calendar', path: '/calendar', label: t('nav.calendar'), visible: true, soon: false },
    { key: 'ai', route: 'ai', path: '/ai', label: t('nav.ai'), visible: !isAdmin.value, soon: false },
    { key: 'people', route: 'users', path: '/users', label: t('nav.people'), visible: can('USER_READ'), soon: false },
  ].filter((item) => item.visible),
)

/** Mục sáng khi đang ở trang đó hoặc trang con của nó (ví dụ /teams/3 sáng mục Nhóm). */
function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <!-- BEGIN: LeftAppRail (markup Stitch) -->
  <nav
    class="w-[3.25rem] bg-[#ebebeb] flex flex-col justify-between items-center py-2 shrink-0 border-r border-[#e0e0e0] max-md:w-full max-md:flex-row max-md:border-r-0 max-md:border-t max-md:py-1 max-md:px-2"
    :aria-label="t('nav.label')"
    data-purpose="app-rail"
  >
    <div class="flex flex-col items-center space-y-4 w-full max-md:flex-row max-md:space-y-0 max-md:justify-around">
      <template
        v-for="item in items"
        :key="item.key"
      >
        <!-- Mục đang chọn -->
        <RouterLink
          v-if="isActive(item.path)"
          :to="{ name: item.route }"
          class="relative flex flex-col items-center group cursor-pointer w-full py-1 max-md:w-auto max-md:px-3"
          :aria-label="labelOf(item)"
          :title="item.soon ? `${item.label} · ${t('soon.badge')}` : item.label"
          aria-current="page"
          :data-testid="`rail-${item.key}`"
        >
          <div class="w-1 absolute left-0 top-1 bottom-1 bg-[#4f52b2] rounded-r max-md:left-3 max-md:right-3 max-md:top-auto max-md:bottom-0 max-md:h-[0.1875rem] max-md:w-auto max-md:rounded-t" />
          <div class="relative w-8 h-8 rounded-lg bg-[#4f52b2]/10 flex items-center justify-center text-[#4f52b2]">
            <svg
              class="w-5 h-5"
              viewBox="0 0 24 24"
              :fill="item.key === 'chat' ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path :d="ICONS[item.key]" />
            </svg>
            <span
              v-if="badgeOf(item.key) > 0"
              class="absolute -right-1 -top-1 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-[#c4314b] px-1 text-[0.625rem] font-bold leading-none text-white"
              :data-testid="item.key === 'activity' ? 'unread-badge' : `${item.key}-badge`"
              aria-hidden="true"
            >{{ badgeOf(item.key) > 99 ? '99+' : badgeOf(item.key) }}</span>
          </div>
        </RouterLink>

        <!-- Mục thường -->
        <RouterLink
          v-else
          :to="{ name: item.route }"
          class="relative w-8 h-8 rounded-lg flex items-center justify-center text-[#616161] hover:bg-black/5 hover:text-black max-md:mx-3"
          :class="{ 'opacity-75': item.soon }"
          :aria-label="labelOf(item)"
          :title="item.soon ? `${item.label} · ${t('soon.badge')}` : item.label"
          :data-testid="`rail-${item.key}`"
        >
          <svg
            class="w-5 h-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path :d="ICONS[item.key]" />
          </svg>
          <span
            v-if="badgeOf(item.key) > 0"
            class="absolute -right-1 -top-1 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-[#c4314b] px-1 text-[0.625rem] font-bold leading-none text-white"
            :data-testid="item.key === 'activity' ? 'unread-badge' : `${item.key}-badge`"
            aria-hidden="true"
          >{{ badgeOf(item.key) > 99 ? '99+' : badgeOf(item.key) }}</span>
        </RouterLink>
      </template>
    </div>
  </nav>
  <!-- END: LeftAppRail -->
</template>

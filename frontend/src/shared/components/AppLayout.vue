<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import NavRail from './NavRail.vue'
import UserMenu from './UserMenu.vue'
import { useAuthStore } from '../../features/auth/store'
import { useChatStore } from '../../features/chat/store'
import ProfileCardHost from '../../features/people/ProfileCardHost.vue'
import { useNotificationStore } from '../../features/notification/store'
import { useCommunityStore } from '../../features/report/store'
import { usePermission } from '../composables/usePermission'

/**
 * Khung ứng dụng. Markup lấy nguyên từ bản thiết kế Stitch (design/homepage-assets/teams.html): khối "TitleBar"
 * (logo, lùi/tới, ô tìm kiếm ở giữa, tài khoản) và "BodyContainer" (thanh ứng dụng + khung nội dung).
 * Bỏ phần giả lập cửa sổ Windows (hình nền, nút thu nhỏ/phóng to/đóng) vì đây là ứng dụng web chạy toàn màn hình.
 * Trang có bố cục nhiều cột (route.meta.fullBleed) tự lo lề và cuộn.
 */
const { t } = useI18n()
const auth = useAuthStore()
const communities = useCommunityStore()
const notifications = useNotificationStore()
const chat = useChatStore()
const { isAdmin } = usePermission()
const router = useRouter()
const route = useRoute()

// Lùi/tới theo lịch sử trình duyệt; phụ thuộc route.fullPath để tính lại sau mỗi lần chuyển trang
const canBack = computed(() => route.fullPath !== null && Boolean(window.history.state?.back))
const canForward = computed(() => route.fullPath !== null && Boolean(window.history.state?.forward))

// Admin không có thông báo (không tham gia công việc hằng ngày) nên không cần hỏi định kỳ
onMounted(() => {
  if (!isAdmin.value) {
    notifications.startPolling()
    chat.start()
  }
})
onBeforeUnmount(() => {
  notifications.stopPolling()
  chat.stop()
})

async function signOut() {
  notifications.reset()
  chat.reset()
  await auth.logout()
  communities.reset() // danh sách cộng đồng thuộc về người vừa đăng xuất
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div
    class="relative w-full h-screen bg-[#f0f2f5] flex flex-col overflow-hidden text-[#242424]"
    data-purpose="teams-app-window"
  >
    <!-- BEGIN: TitleBar (markup Stitch) -->
    <header class="h-[2.75rem] bg-[#edebe9]/80 backdrop-blur-md flex items-center justify-between px-3 shrink-0 border-b border-[#e1dfdd] text-xs">
      <!-- Left side controls / navigation -->
      <div class="flex items-center space-x-3 w-1/4">
        <RouterLink
          :to="{ name: 'home' }"
          class="w-6 h-6 flex items-center justify-center rounded bg-[#4f52b2] text-white font-bold text-xs shadow-sm hover:brightness-110 focus-visible:ring-2 focus-visible:ring-[#4f52b2] focus-visible:ring-offset-2"
          :aria-label="t('nav.home')"
          :title="t('nav.home')"
          data-testid="logo-home"
        >
          報
        </RouterLink>
        <div class="flex items-center space-x-1 text-[#616161] max-md:hidden">
          <button
            type="button"
            class="p-1 rounded hover:bg-black/5 hover:text-black disabled:opacity-40 disabled:cursor-default disabled:hover:bg-transparent"
            :disabled="!canBack"
            :aria-label="t('nav.back')"
            data-testid="nav-back"
            @click="router.back()"
          >
            <svg
              class="w-3.5 h-3.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            ><path
              d="M15 19l-7-7 7-7"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
            /></svg>
          </button>
          <button
            type="button"
            class="p-1 rounded hover:bg-black/5 hover:text-black disabled:opacity-40 disabled:cursor-default disabled:hover:bg-transparent"
            :disabled="!canForward"
            :aria-label="t('nav.forward')"
            data-testid="nav-forward"
            @click="router.forward()"
          >
            <svg
              class="w-3.5 h-3.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            ><path
              d="M9 5l7 7-7 7"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
            /></svg>
          </button>
        </div>
      </div>

      <!-- Center Search Bar (tìm kiếm toàn ứng dụng chưa làm: giữ chỗ đúng thiết kế, tạm khoá) -->
      <div
        class="w-full max-w-[28.75rem] relative max-md:hidden"
        :title="t('soon.badge')"
      >
        <div class="flex items-center bg-white rounded-md h-[1.75rem] px-2.5 shadow-sm border border-transparent focus-within:border-[#4f52b2] transition-colors">
          <svg
            class="w-3.5 h-3.5 text-[#616161] mr-2 shrink-0"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
            />
          </svg>
          <input
            class="w-full border-none p-0 text-xs text-[#242424] placeholder-[#707070] bg-transparent focus:ring-0 outline-none"
            :placeholder="t('nav.search')"
            :aria-label="t('nav.search')"
            type="text"
            disabled
          >
        </div>
      </div>

      <!-- Right side: tài khoản -->
      <div class="flex items-center justify-end space-x-3 w-1/4">
        <UserMenu @sign-out="signOut" />
      </div>
    </header>
    <!-- END: TitleBar -->

    <!-- BEGIN: BodyContainer (markup Stitch) -->
    <div class="flex-1 flex overflow-hidden max-md:flex-col-reverse">
      <NavRail />
      <main
        class="flex-1 min-w-0 bg-white"
        :class="route.meta.fullBleed ? 'overflow-hidden' : 'overflow-auto px-7 py-6 max-md:p-4'"
      >
        <RouterView />
      </main>
    </div>
    <!-- END: BodyContainer -->

    <!-- Thẻ hồ sơ một người, mở từ tên/ảnh đại diện ở bất kỳ đâu -->
    <ProfileCardHost />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../auth/store'
import AdminDashboard from '../components/AdminDashboard.vue'
import ManagerDashboard from '../components/ManagerDashboard.vue'
import UserDashboard from '../components/UserDashboard.vue'
import { dayPart } from '../summary'

/**
 * Trang chủ (bấm vào logo): tổng quan theo vai trò. Thành viên/Leader thấy việc, báo cáo hôm nay, lịch; Manager thấy tình
 * trạng nộp báo cáo của các cộng đồng mình quản lý; Admin thấy số liệu tài khoản và nhóm.
 */
const { t, locale } = useI18n()
const auth = useAuthStore()

const now = new Date()
const greeting = computed(() => t(`dashboard.greeting.${dayPart(now)}`, { name: auth.user?.fullName ?? '' }))
const dateText = computed(() =>
  now.toLocaleDateString(locale.value, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }),
)
</script>

<template>
  <div
    v-if="auth.user"
    class="h-full overflow-y-auto bg-[#f5f5f5] px-7 py-6 max-md:px-4"
    data-testid="dashboard"
  >
    <header class="mb-5">
      <h1 class="text-[1.375rem] font-bold text-[#242424]">
        {{ greeting }}
      </h1>
      <p class="mt-0.5 text-[0.8125rem] capitalize text-[#616161]">
        {{ dateText }}
        <span class="normal-case">
          · {{ t(`users.roles.${auth.user.systemRole}`) }}<template v-if="auth.user.position"> · {{ auth.user.position }}</template>
        </span>
      </p>
    </header>

    <AdminDashboard v-if="auth.user.systemRole === 'ADMIN'" />
    <ManagerDashboard v-else-if="auth.user.systemRole === 'MANAGER'" />
    <UserDashboard v-else />
  </div>
</template>

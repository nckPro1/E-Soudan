<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as teamApi from '../../team/api'
import * as userApi from '../../user/api'
import DashSection from './DashSection.vue'
import StatCard from './StatCard.vue'

/**
 * Tổng quan của Admin: số tài khoản (đang hoạt động, bị khoá, theo vai trò), số nhóm, và lối tắt tới các việc quản trị.
 * Chỉ đọc số tổng nên mỗi con số là một lời gọi nhẹ (size=1).
 */
const { t } = useI18n()

const loading = ref(true)
const failed = ref(false)
const stats = ref({ users: 0, locked: 0, managers: 0, members: 0, teams: 0, archived: 0 })

const total = (query: Parameters<typeof userApi.listUsers>[0]) => userApi.listUsers(query).then((r) => r.totalItems)

onMounted(async () => {
  const [users, locked, managers, members, teams, archived] = await Promise.allSettled([
    total({ page: 0, size: 1 }),
    total({ page: 0, size: 1, status: 'LOCKED' }),
    total({ page: 0, size: 1, role: 'MANAGER' }),
    total({ page: 0, size: 1, role: 'USER' }),
    teamApi.listTeams({ page: 0, size: 1, status: 'ACTIVE' }).then((r) => r.totalItems),
    teamApi.listTeams({ page: 0, size: 1, status: 'ARCHIVED' }).then((r) => r.totalItems),
  ])
  const value = (r: PromiseSettledResult<number>) => (r.status === 'fulfilled' ? r.value : 0)
  failed.value = [users, locked, managers, members, teams, archived].some((r) => r.status === 'rejected')
  stats.value = {
    users: value(users),
    locked: value(locked),
    managers: value(managers),
    members: value(members),
    teams: value(teams),
    archived: value(archived),
  }
  loading.value = false
})
</script>

<template>
  <div data-testid="admin-dashboard">
    <p
      v-if="failed"
      class="mb-3 rounded-md bg-[#fff8e5] px-3 py-2 text-xs text-[#7a5b00]"
      data-testid="dashboard-partial"
    >
      {{ t('dashboard.partialError') }}
    </p>

    <div class="grid grid-cols-4 gap-3 max-lg:grid-cols-2 max-sm:grid-cols-1">
      <StatCard
        :label="t('dashboard.stats.accounts')"
        :value="loading ? '…' : stats.users"
        :hint="t('dashboard.accountsHint', { managers: stats.managers, members: stats.members })"
        :to="{ name: 'users' }"
      />
      <StatCard
        :label="t('dashboard.stats.locked')"
        :value="loading ? '…' : stats.locked"
        :hint="t('dashboard.lockedHint')"
        :tone="stats.locked > 0 ? 'warning' : 'success'"
        :to="{ name: 'users' }"
      />
      <StatCard
        :label="t('dashboard.stats.teams')"
        :value="loading ? '…' : stats.teams"
        :hint="t('dashboard.archivedHint', { count: stats.archived })"
        :to="{ name: 'teams' }"
      />
      <StatCard
        :label="t('dashboard.stats.calendar')"
        :value="t('dashboard.open')"
        :hint="t('dashboard.calendarHint')"
        :to="{ name: 'calendar' }"
      />
    </div>

    <div class="mt-4">
      <DashSection :title="t('dashboard.adminShortcuts')">
        <ul class="divide-y divide-[#edebe9]">
          <li>
            <RouterLink
              :to="{ name: 'users' }"
              class="flex items-center justify-between py-3 text-[0.8125rem] hover:bg-[#f5f5f5]"
            >
              <span><strong class="font-semibold">{{ t('dashboard.manageAccounts') }}</strong>
                <span class="ml-2 text-xs text-[#616161]">{{ t('dashboard.manageAccountsHint') }}</span></span>
              <span aria-hidden="true">→</span>
            </RouterLink>
          </li>
          <li>
            <RouterLink
              :to="{ name: 'teams' }"
              class="flex items-center justify-between py-3 text-[0.8125rem] hover:bg-[#f5f5f5]"
            >
              <span><strong class="font-semibold">{{ t('dashboard.manageTeams') }}</strong>
                <span class="ml-2 text-xs text-[#616161]">{{ t('dashboard.manageTeamsHint') }}</span></span>
              <span aria-hidden="true">→</span>
            </RouterLink>
          </li>
        </ul>
      </DashSection>
    </div>
  </div>
</template>

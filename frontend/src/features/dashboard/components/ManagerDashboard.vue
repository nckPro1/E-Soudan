<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '../../chat/store'
import * as notificationApi from '../../notification/api'
import NotificationItem from '../../notification/components/NotificationItem.vue'
import { useNotificationStore } from '../../notification/store'
import { notificationTarget } from '../../notification/target'
import type { AppNotification } from '../../notification/types'
import * as reportApi from '../../report/api'
import { useCommunityStore } from '../../report/store'
import type { CommunitySummary, PendingReport } from '../../report/types'
import * as teamApi from '../../team/api'
import type { TeamSummary } from '../../team/types'
import DashSection from './DashSection.vue'
import StatCard from './StatCard.vue'

/**
 * Tổng quan của Manager: tình trạng nộp báo cáo hôm nay ở từng cộng đồng mình quản lý (ai đã nộp, ai chưa, ai nghỉ),
 * các nhóm mình quan sát, hoạt động gần đây. Ngày không phải ngày làm việc thì không ai bị tính là chưa nộp.
 */
interface Row {
  community: CommunitySummary
  pending: PendingReport | null
}

const { t } = useI18n()
const router = useRouter()
const chat = useChatStore()
const notifications = useNotificationStore()
const communities = useCommunityStore()

const loading = ref(true)
const failed = ref(false)
const rows = ref<Row[]>([])
const teams = ref<TeamSummary[]>([])
const activity = ref<AppNotification[]>([])

const missingTotal = computed(() => rows.value.reduce((n, r) => n + (r.pending?.missing.length ?? 0), 0))
const leaveTotal = computed(() => rows.value.reduce((n, r) => n + (r.pending?.onLeave.length ?? 0), 0))
const unreadTotal = computed(() => chat.totalUnread + notifications.unread)
const workingDay = computed(() => rows.value.length === 0 || rows.value.some((r) => r.pending?.workingDay))

async function loadCommunities() {
  await communities.load()
  const owned = communities.sorted.filter((c) => c.owned)
  rows.value = await Promise.all(
    owned.map(async (community) => ({ community, pending: await reportApi.getPending(community.id).catch(() => null) })),
  )
}

onMounted(async () => {
  const results = await Promise.allSettled([
    loadCommunities(),
    teamApi.listTeams({ page: 0, size: 6, status: 'ACTIVE' }).then((r) => (teams.value = r.items)),
    notificationApi.listNotifications(false, 0, 5).then((r) => (activity.value = r.items)),
  ])
  failed.value = results.some((r) => r.status === 'rejected')
  loading.value = false
})

/** Số người đã nộp = tổng thành viên trừ người chưa nộp và người nghỉ (chỉ tính khi là ngày làm việc). */
const submitted = (row: Row) =>
  row.pending ? Math.max(row.community.memberCount - row.pending.missing.length - row.pending.onLeave.length, 0) : 0
const percent = (row: Row) =>
  row.community.memberCount === 0 ? 0 : Math.round((submitted(row) / row.community.memberCount) * 100)

const openNotification = (n: AppNotification) => {
  const to = notificationTarget(n)
  if (to) void router.push(to)
}
</script>

<template>
  <div data-testid="manager-dashboard">
    <p
      v-if="failed"
      class="mb-3 rounded-md bg-[#fff8e5] px-3 py-2 text-xs text-[#7a5b00]"
      data-testid="dashboard-partial"
    >
      {{ t('dashboard.partialError') }}
    </p>

    <div class="grid grid-cols-4 gap-3 max-lg:grid-cols-2 max-sm:grid-cols-1">
      <StatCard
        :label="t('dashboard.stats.communities')"
        :value="loading ? '…' : rows.length"
        :to="{ name: 'communities' }"
      />
      <StatCard
        :label="t('dashboard.stats.missingToday')"
        :value="loading ? '…' : missingTotal"
        :hint="workingDay ? t('dashboard.missingHint') : t('dashboard.notWorkingDay')"
        :tone="missingTotal > 0 ? 'warning' : 'success'"
        :to="{ name: 'communities' }"
      />
      <StatCard
        :label="t('dashboard.stats.onLeave')"
        :value="loading ? '…' : leaveTotal"
        :hint="t('dashboard.onLeaveHint')"
      />
      <StatCard
        :label="t('dashboard.stats.unread')"
        :value="unreadTotal"
        :hint="t('dashboard.unreadHint', { chat: chat.totalUnread, activity: notifications.unread })"
        :tone="unreadTotal > 0 ? 'warning' : 'neutral'"
        :to="chat.totalUnread > 0 ? { name: 'chat' } : { name: 'activity' }"
      />
    </div>

    <div class="mt-4 grid grid-cols-[3fr_2fr] gap-4 max-lg:grid-cols-1">
      <div class="space-y-4">
        <DashSection
          :title="t('dashboard.reportsByCommunity')"
          :to="{ name: 'communities' }"
          :link-label="t('dashboard.seeAll')"
        >
          <p
            v-if="!loading && rows.length === 0"
            class="py-6 text-center text-[0.8125rem] text-[#616161]"
          >
            {{ t('dashboard.noOwnedCommunity') }}
          </p>
          <ul class="divide-y divide-[#edebe9]">
            <li
              v-for="row in rows"
              :key="row.community.id"
              class="py-3"
              data-testid="community-row"
            >
              <div class="mb-1.5 flex items-baseline justify-between gap-3">
                <RouterLink
                  :to="{ name: 'community-feed', params: { id: row.community.id } }"
                  class="min-w-0 truncate text-[0.8125rem] font-semibold hover:underline"
                >
                  {{ row.community.name }}
                </RouterLink>
                <span class="shrink-0 text-xs text-[#616161]">
                  <template v-if="row.pending?.workingDay">{{ submitted(row) }}/{{ row.community.memberCount }} {{ t('dashboard.submittedShort') }}</template>
                  <template v-else>{{ t('dashboard.notWorkingDayShort') }}</template>
                </span>
              </div>
              <div
                class="h-2 overflow-hidden rounded-full bg-[#edebe9]"
                role="progressbar"
                :aria-valuenow="percent(row)"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div
                  class="h-full rounded-full bg-[#107c41] transition-all"
                  :style="{ width: row.pending?.workingDay ? `${percent(row)}%` : '0%' }"
                />
              </div>
              <p
                v-if="row.pending && row.pending.missing.length > 0"
                class="mt-1.5 text-xs text-[#616161]"
                data-testid="missing-names"
              >
                {{ t('dashboard.missing') }}:
                <RouterLink
                  v-for="(p, i) in row.pending.missing"
                  :key="p.id"
                  :to="{ name: 'member-reports', params: { id: row.community.id, userId: p.id } }"
                  class="link"
                >{{ p.fullName }}<template v-if="i < row.pending.missing.length - 1">, </template></RouterLink>
              </p>
              <p
                v-if="row.pending && row.pending.onLeave.length > 0"
                class="mt-0.5 text-xs text-[#616161]"
              >
                {{ t('report.onLeave') }}: {{ row.pending.onLeave.map((p) => p.fullName).join(', ') }}
              </p>
            </li>
          </ul>
        </DashSection>

        <DashSection
          :title="t('dashboard.recentActivity')"
          :to="{ name: 'activity' }"
          :link-label="t('dashboard.seeAll')"
        >
          <p
            v-if="!loading && activity.length === 0"
            class="py-6 text-center text-[0.8125rem] text-[#616161]"
          >
            {{ t('notif.empty') }}
          </p>
          <div class="-mx-4">
            <NotificationItem
              v-for="n in activity"
              :key="n.id"
              :notification="n"
              @open="openNotification(n)"
            />
          </div>
        </DashSection>
      </div>

      <DashSection
        :title="t('dashboard.observedTeams')"
        :to="{ name: 'teams' }"
        :link-label="t('dashboard.seeAll')"
      >
        <p
          v-if="!loading && teams.length === 0"
          class="py-6 text-center text-[0.8125rem] text-[#616161]"
        >
          {{ t('dashboard.noTeams') }}
        </p>
        <ul class="divide-y divide-[#edebe9]">
          <li
            v-for="team in teams"
            :key="team.id"
          >
            <RouterLink
              :to="{ name: 'team-detail', params: { id: team.id } }"
              class="flex items-center justify-between gap-3 py-2.5 hover:bg-[#f5f5f5]"
            >
              <span class="min-w-0 truncate text-[0.8125rem] font-semibold">{{ team.name }}</span>
              <span class="shrink-0 text-xs text-[#616161]">{{ t('team.memberCountLabel', { count: team.memberCount }) }}</span>
            </RouterLink>
          </li>
        </ul>
      </DashSection>
    </div>
  </div>
</template>

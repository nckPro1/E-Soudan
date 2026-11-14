<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../auth/store'
import * as calendarApi from '../../calendar/api'
import { addDays as addKeyDays } from '../summary'
import { fromKey, todayKey } from '../../calendar/dates'
import { COLOR_HEX, type CalendarNote } from '../../calendar/types'
import { useChatStore } from '../../chat/store'
import * as notificationApi from '../../notification/api'
import NotificationItem from '../../notification/components/NotificationItem.vue'
import { useNotificationStore } from '../../notification/store'
import { notificationTarget } from '../../notification/target'
import type { AppNotification } from '../../notification/types'
import * as reportApi from '../../report/api'
import { useCommunityStore } from '../../report/store'
import type { CommunitySummary } from '../../report/types'
import * as taskApi from '../../task/api'
import TaskStatusChip from '../../task/components/TaskStatusChip.vue'
import type { Task } from '../../task/types'
import { digestTasks } from '../summary'
import DashSection from './DashSection.vue'
import StatCard from './StatCard.vue'

/**
 * Tổng quan của Thành viên / Leader: việc cần chú ý (quá hạn, sắp đến hạn), tình trạng báo cáo hôm nay ở từng cộng đồng,
 * lịch 7 ngày tới, hoạt động gần đây và số tin chưa đọc. Dữ liệu tải song song; một phần lỗi không làm hỏng cả trang.
 */
type ReportState = 'submitted' | 'leave' | 'missing'
interface ReportRow {
  community: CommunitySummary
  state: ReportState
}

const { t, locale } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()
const notifications = useNotificationStore()
const communities = useCommunityStore()

const today = todayKey()
const loading = ref(true)
const failed = ref(false)
const tasks = ref<Task[]>([])
const reports = ref<ReportRow[]>([])
const notes = ref<CalendarNote[]>([])
const activity = ref<AppNotification[]>([])

const digest = computed(() => digestTasks(tasks.value, today))
const submittedCount = computed(() => reports.value.filter((r) => r.state !== 'missing').length)
const unreadTotal = computed(() => chat.totalUnread + notifications.unread)
/** Cuối tuần mặc định không bắt buộc nộp báo cáo (app.report.work-days), nên thiếu báo cáo thì không tô cảnh báo. */
const weekend = [0, 6].includes(new Date().getDay())

async function loadReports() {
  await communities.load()
  const mine = communities.sorted.filter((c) => !c.owned)
  if (mine.length === 0) {
    reports.value = []
    return
  }
  const off = await reportApi.listDaysOff(today, today).catch(() => [])
  const onLeave = off.some((d) => d.day === today)
  reports.value = await Promise.all(
    mine.map(async (community) => {
      const post = await reportApi.getMyToday(community.id).catch(() => null)
      const state: ReportState = post ? 'submitted' : onLeave ? 'leave' : 'missing'
      return { community, state }
    }),
  )
}

onMounted(async () => {
  const results = await Promise.allSettled([
    taskApi.listMyTasks({ page: 0, size: 100 }).then((r) => (tasks.value = r.items)),
    loadReports(),
    calendarApi.listNotes(today, addKeyDays(today, 6)).then((r) => (notes.value = r)),
    notificationApi.listNotifications(false, 0, 5).then((r) => (activity.value = r.items)),
  ])
  failed.value = results.some((r) => r.status === 'rejected')
  loading.value = false
})

function dueLabel(task: Task): { text: string; tone: string } {
  if (!task.dueDate) return { text: t('dashboard.noDue'), tone: 'text-[#616161]' }
  const diff = Math.round((fromKey(task.dueDate).getTime() - fromKey(today).getTime()) / 86400000)
  if (diff < 0) return { text: t('dashboard.overdueDays', { count: -diff }), tone: 'font-semibold text-[#c4314b]' }
  if (diff === 0) return { text: t('dashboard.dueToday'), tone: 'font-semibold text-[#ca5010]' }
  if (diff === 1) return { text: t('dashboard.dueTomorrow'), tone: 'font-semibold text-[#ca5010]' }
  return {
    text: fromKey(task.dueDate).toLocaleDateString(locale.value, { day: 'numeric', month: 'numeric' }),
    tone: 'text-[#616161]',
  }
}

const dayLabel = (key: string) =>
  key === today
    ? t('chat.today')
    : fromKey(key).toLocaleDateString(locale.value, { weekday: 'short', day: 'numeric', month: 'numeric' })

const openNotification = (n: AppNotification) => {
  const to = notificationTarget(n)
  if (to) void router.push(to)
}
</script>

<template>
  <div data-testid="user-dashboard">
    <p
      v-if="failed"
      class="mb-3 rounded-md bg-[#fff8e5] px-3 py-2 text-xs text-[#7a5b00]"
      data-testid="dashboard-partial"
    >
      {{ t('dashboard.partialError') }}
    </p>

    <div class="grid grid-cols-4 gap-3 max-lg:grid-cols-2 max-sm:grid-cols-1">
      <StatCard
        :label="t('dashboard.stats.openTasks')"
        :value="loading ? '…' : digest.open.length"
        :hint="digest.waitingReview.length > 0 ? t('dashboard.waitingReview', { count: digest.waitingReview.length }) : ''"
        :to="{ name: 'my-tasks' }"
      />
      <StatCard
        :label="t('dashboard.stats.attention')"
        :value="loading ? '…' : digest.overdue.length"
        :hint="t('dashboard.attentionHint', { count: digest.dueSoon.length })"
        :tone="digest.overdue.length > 0 ? 'danger' : 'success'"
        :to="{ name: 'my-tasks' }"
      />
      <StatCard
        :label="t('dashboard.stats.reportsToday')"
        :value="loading ? '…' : reports.length === 0 ? '—' : `${submittedCount}/${reports.length}`"
        :hint="reports.length === 0 ? t('dashboard.noCommunity') : t('dashboard.submittedHint')"
        :tone="reports.length > 0 && submittedCount < reports.length && !weekend ? 'warning' : 'success'"
        :to="{ name: 'communities' }"
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
          :title="t('dashboard.attentionTasks')"
          :to="{ name: 'my-tasks' }"
          :link-label="t('dashboard.seeAll')"
        >
          <p
            v-if="!loading && digest.attention.length === 0"
            class="py-6 text-center text-[0.8125rem] text-[#616161]"
            data-testid="no-tasks"
          >
            {{ t('dashboard.noTasks') }}
          </p>
          <ul class="divide-y divide-[#edebe9]">
            <li
              v-for="task in digest.attention"
              :key="task.id"
              data-testid="attention-task"
            >
              <RouterLink
                :to="{ name: 'task-detail', params: { id: task.id } }"
                class="flex items-center gap-3 py-2.5 hover:bg-[#f5f5f5]"
              >
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-[0.8125rem] font-semibold">{{ task.title }}</span>
                  <span class="block truncate text-xs text-[#616161]">{{ task.teamName ?? '' }}</span>
                </span>
                <span
                  class="shrink-0 text-xs"
                  :class="dueLabel(task).tone"
                >{{ dueLabel(task).text }}</span>
                <TaskStatusChip :status="task.status" />
              </RouterLink>
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

      <div class="space-y-4">
        <DashSection
          :title="t('dashboard.reportsToday')"
          :to="{ name: 'communities' }"
          :link-label="t('dashboard.seeAll')"
        >
          <p
            v-if="!loading && reports.length === 0"
            class="py-6 text-center text-[0.8125rem] text-[#616161]"
          >
            {{ t('dashboard.noCommunity') }}
          </p>
          <ul class="divide-y divide-[#edebe9]">
            <li
              v-for="row in reports"
              :key="row.community.id"
              class="flex items-center gap-3 py-2.5"
              data-testid="report-row"
            >
              <span class="min-w-0 flex-1 truncate text-[0.8125rem] font-semibold">{{ row.community.name }}</span>
              <span
                class="shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold"
                :class="{
                  'bg-[#107c41]/10 text-[#107c41]': row.state === 'submitted',
                  'bg-[#4f52b2]/10 text-[#4f52b2]': row.state === 'leave',
                  'bg-[#ca5010]/10 text-[#ca5010]': row.state === 'missing' && !weekend,
                  'bg-[#edebe9] text-[#616161]': row.state === 'missing' && weekend,
                }"
              >{{ t(`dashboard.reportState.${row.state}`) }}</span>
              <RouterLink
                v-if="auth.user"
                :to="{ name: 'member-reports', params: { id: row.community.id, userId: auth.user.id } }"
                class="link shrink-0 text-xs"
              >
                {{ row.state === 'missing' ? t('dashboard.writeReport') : t('dashboard.viewReport') }}
              </RouterLink>
            </li>
          </ul>
        </DashSection>

        <DashSection
          :title="t('dashboard.upcoming')"
          :to="{ name: 'calendar' }"
          :link-label="t('dashboard.openCalendar')"
        >
          <p
            v-if="!loading && notes.length === 0"
            class="py-6 text-center text-[0.8125rem] text-[#616161]"
          >
            {{ t('dashboard.noNotes') }}
          </p>
          <ul class="divide-y divide-[#edebe9]">
            <li
              v-for="note in notes"
              :key="note.id"
              class="flex items-center gap-3 py-2.5"
              data-testid="upcoming-note"
            >
              <span
                class="h-2.5 w-2.5 shrink-0 rounded-full"
                :style="{ background: COLOR_HEX[note.color] }"
                aria-hidden="true"
              />
              <span class="min-w-0 flex-1 truncate text-[0.8125rem]">{{ note.title }}</span>
              <span class="shrink-0 text-xs capitalize text-[#616161]">{{ dayLabel(note.date) }}</span>
            </li>
          </ul>
        </DashSection>
      </div>
    </div>
  </div>
</template>

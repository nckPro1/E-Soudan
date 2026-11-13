<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { usePermission } from '../../../shared/composables/usePermission'
import { toApiError } from '../../../shared/api/http'
import { colorOf, tileLabelOf } from '../../../shared/utils/avatar'
import { useChatStore } from '../../chat/store'
import * as teamApi from '../api'
import TeamFormDialog from '../components/TeamFormDialog.vue'
import type { TeamDetail, TeamStatus, TeamSummary } from '../types'

/**
 * App Nhóm, bố cục như Teams: cột trái là danh sách nhóm (ô tìm, chip Đang hoạt động/Đã lưu trữ, chấm số tin chưa đọc
 * của phòng chat từng nhóm), khung phải là nhóm đang chọn với các tab Trò chuyện / Thành viên / Công việc.
 * Chỉ Admin tạo nhóm. Màn hình hẹp chỉ hiện một cột tại một thời điểm.
 */
const LIST_SIZE = 100
const SEARCH_DEBOUNCE_MS = 300

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const { can } = usePermission()
const chat = useChatStore()

const teams = ref<TeamSummary[]>([])
const loading = ref(false)
const errorCode = ref('')
const search = ref('')
const status = ref<TeamStatus>('ACTIVE')
const creating = ref(false)

const selectedId = computed(() => (route.params.id ? Number(route.params.id) : null))
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

let seq = 0
async function load() {
  const mine = ++seq
  loading.value = true
  errorCode.value = ''
  try {
    const result = await teamApi.listTeams({ page: 0, size: LIST_SIZE, q: search.value.trim(), status: status.value })
    if (mine === seq) teams.value = result.items
  } catch (error) {
    if (mine === seq) errorCode.value = toApiError(error).code
  } finally {
    if (mine === seq) loading.value = false
  }
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(timer)
  timer = setTimeout(() => void load(), SEARCH_DEBOUNCE_MS)
})
watch(status, () => void load())
// Sau khi sửa/lưu trữ một nhóm ở khung phải, danh sách bên trái cần cập nhật
watch(
  () => route.fullPath,
  () => void load(),
)
onMounted(load)

function toggleStatus(value: TeamStatus) {
  status.value = status.value === value ? (value === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE') : value
}

async function onCreated(team: TeamDetail) {
  creating.value = false
  await router.push({ name: 'team-detail', params: { id: team.id } })
}
</script>

<template>
  <div class="flex h-full">
    <aside
      class="flex w-[17.5rem] shrink-0 flex-col border-r border-[#e0e0e0] bg-[#f5f5f5] max-md:w-full max-md:border-r-0"
      :class="{ 'max-md:hidden': selectedId !== null }"
      :aria-label="t('team.title')"
      data-purpose="chat-sidebar"
    >
      <div class="flex items-center justify-between px-4 pb-2 pt-3">
        <h1 class="text-xl font-bold text-[#242424]">
          {{ t('team.title') }}
        </h1>
        <button
          v-if="can('TEAM_MANAGE')"
          type="button"
          class="rounded p-1 text-[#616161] hover:bg-black/5"
          :title="t('team.create')"
          :aria-label="t('team.create')"
          data-testid="create-team"
          @click="creating = true"
        >
          <svg
            class="h-[1.125rem] w-[1.125rem]"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            aria-hidden="true"
          ><path d="M12 5v14M5 12h14" /></svg>
        </button>
      </div>

      <div class="px-3 pb-2">
        <input
          v-model="search"
          type="search"
          class="h-8 w-full rounded-md border border-transparent bg-white px-2.5 text-xs outline-none placeholder:text-[#707070] focus:border-[#4f52b2]"
          :placeholder="t('team.searchPlaceholder')"
          :aria-label="t('team.searchPlaceholder')"
          data-testid="team-search"
        >
      </div>

      <div class="flex gap-1.5 px-3 pb-2 text-xs">
        <button
          type="button"
          class="rounded-full border px-2.5 py-0.5"
          :class="status === 'ACTIVE' ? 'border-[#4f52b2] bg-[#4f52b2] text-white' : 'border-[#d1d1d1] bg-white text-[#424242] hover:bg-[#edebe9]'"
          :aria-pressed="status === 'ACTIVE'"
          @click="toggleStatus('ACTIVE')"
        >
          {{ t('team.filterActive') }}
        </button>
        <button
          type="button"
          class="rounded-full border px-2.5 py-0.5"
          :class="status === 'ARCHIVED' ? 'border-[#4f52b2] bg-[#4f52b2] text-white' : 'border-[#d1d1d1] bg-white text-[#424242] hover:bg-[#edebe9]'"
          :aria-pressed="status === 'ARCHIVED'"
          @click="toggleStatus('ARCHIVED')"
        >
          {{ t('team.filterArchived') }}
        </button>
      </div>

      <AlertBanner
        v-if="errorMessage"
        class="mx-3 mb-2"
      >
        {{ errorMessage }}
      </AlertBanner>

      <div
        class="min-h-0 flex-1 overflow-y-auto px-2 pb-3"
        data-testid="team-list"
      >
        <RouterLink
          v-for="team in teams"
          :key="team.id"
          :to="{ name: 'team-detail', params: { id: team.id } }"
          class="flex items-center gap-2.5 rounded-lg px-2 py-2"
          :class="selectedId === team.id ? 'bg-white shadow-sm ring-1 ring-[#e1dfdd]' : 'hover:bg-[#edebe9]'"
          :aria-current="selectedId === team.id ? 'page' : undefined"
          data-testid="team-item"
        >
          <span
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
            :style="{ background: colorOf(team.name) }"
            aria-hidden="true"
          >{{ tileLabelOf(team.name) }}</span>
          <span class="min-w-0 flex-1">
            <span
              class="block truncate text-[0.8125rem]"
              :class="(chat.unreadByTeam[team.id] ?? 0) > 0 ? 'font-bold' : 'font-semibold'"
            >{{ team.name }}</span>
            <span class="block truncate text-xs text-[#616161]">{{ t('team.memberCountLabel', { count: team.memberCount }) }}</span>
          </span>
          <span
            v-if="(chat.unreadByTeam[team.id] ?? 0) > 0"
            class="flex h-4 min-w-[1rem] shrink-0 items-center justify-center rounded-full bg-[#c4314b] px-1 text-[0.625rem] font-bold leading-none text-white"
            data-testid="team-unread"
          >{{ chat.unreadByTeam[team.id] }}</span>
        </RouterLink>

        <p
          v-if="!loading && teams.length === 0"
          class="px-3 py-6 text-center text-xs text-[#616161]"
        >
          {{ t('team.empty') }}
        </p>
        <p
          v-else-if="loading && teams.length === 0"
          class="px-3 py-6 text-center text-xs text-[#616161]"
        >
          {{ t('common.loading') }}
        </p>
      </div>
    </aside>

    <section
      class="min-w-0 flex-1 overflow-hidden bg-white"
      :class="{ 'max-md:hidden': selectedId === null }"
    >
      <RouterView :key="route.params.id ? String(route.params.id) : 'none'" />
    </section>

    <TeamFormDialog
      v-if="creating"
      :team="null"
      @close="creating = false"
      @saved="onCreated"
    />
  </div>
</template>

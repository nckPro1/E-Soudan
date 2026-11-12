<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { usePermission } from '../../../shared/composables/usePermission'
import { toApiError } from '../../../shared/api/http'
import type { PageResult } from '../../../shared/types/api'
import * as teamApi from '../api'
import TeamFormDialog from '../components/TeamFormDialog.vue'
import type { TeamDetail, TeamStatus, TeamSummary } from '../types'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

const { t, te } = useI18n()
const router = useRouter()
const { can } = usePermission()

const result = ref<PageResult<TeamSummary> | null>(null)
const loading = ref(false)
const errorCode = ref('')
const page = ref(0)
const search = ref('')
const status = ref<TeamStatus>('ACTIVE')
const creating = ref(false)

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

let requestSeq = 0

async function load() {
  const seq = ++requestSeq
  loading.value = true
  errorCode.value = ''
  try {
    const data = await teamApi.listTeams({
      page: page.value,
      size: PAGE_SIZE,
      q: search.value.trim(),
      status: status.value,
    })
    if (seq === requestSeq) result.value = data
  } catch (error) {
    if (seq === requestSeq) errorCode.value = toApiError(error).code
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(timer)
  timer = setTimeout(() => {
    page.value = 0
    void load()
  }, SEARCH_DEBOUNCE_MS)
})
watch(status, () => {
  page.value = 0
  void load()
})
onMounted(load)
onUnmounted(() => clearTimeout(timer))

function goTo(target: number) {
  page.value = target
  void load()
}

async function onCreated(team: TeamDetail) {
  creating.value = false
  await router.push({ name: 'team-detail', params: { id: team.id } })
}
</script>

<template>
  <div>
    <div class="head">
      <h1>{{ t('team.title') }}</h1>
      <button
        v-if="can('TEAM_MANAGE')"
        type="button"
        class="btn primary"
        @click="creating = true"
      >
        {{ t('team.create') }}
      </button>
    </div>

    <div class="filters">
      <input
        v-model="search"
        type="search"
        class="control search"
        :placeholder="t('team.searchPlaceholder')"
        :aria-label="t('team.searchPlaceholder')"
      >
      <select
        v-model="status"
        class="control"
        :aria-label="t('users.status')"
      >
        <option value="ACTIVE">
          {{ t('team.statuses.ACTIVE') }}
        </option>
        <option value="ARCHIVED">
          {{ t('team.statuses.ARCHIVED') }}
        </option>
      </select>
    </div>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>{{ t('team.name') }}</th>
            <th>{{ t('team.description') }}</th>
            <th>{{ t('team.memberCount') }}</th>
            <th>{{ t('users.status') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="team in result?.items ?? []"
            :key="team.id"
          >
            <td>
              <RouterLink
                :to="{ name: 'team-detail', params: { id: team.id } }"
                class="name"
              >
                {{ team.name }}
              </RouterLink>
            </td>
            <td class="muted">
              {{ team.description ?? '' }}
            </td>
            <td>{{ team.memberCount }}</td>
            <td>
              <span
                class="badge"
                :class="team.status === 'ARCHIVED' ? 'archived' : 'active'"
              >{{ t(`team.statuses.${team.status}`) }}</span>
            </td>
          </tr>
          <tr v-if="!loading && result && result.items.length === 0">
            <td
              colspan="4"
              class="empty"
            >
              {{ t('team.empty') }}
            </td>
          </tr>
          <tr v-if="loading && !result">
            <td
              colspan="4"
              class="empty"
            >
              {{ t('common.loading') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="result && result.totalPages > 1"
      class="pager"
    >
      <button
        type="button"
        class="btn secondary"
        :disabled="page === 0 || loading"
        @click="goTo(page - 1)"
      >
        {{ t('common.prev') }}
      </button>
      <span>{{ t('common.pageOf', { page: page + 1, total: result.totalPages }) }}</span>
      <button
        type="button"
        class="btn secondary"
        :disabled="page + 1 >= result.totalPages || loading"
        @click="goTo(page + 1)"
      >
        {{ t('common.next') }}
      </button>
    </div>

    <TeamFormDialog
      v-if="creating"
      :team="null"
      @close="creating = false"
      @saved="onCreated"
    />
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

h1 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.control {
  height: 36px;
  padding: 0 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--color-text);
}

.search {
  flex: 1;
  min-width: 200px;
  max-width: 360px;
}

.table-wrap {
  overflow-x: auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

th {
  background: var(--color-surface-muted);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.name {
  color: var(--color-primary);
  font-weight: 500;
  text-decoration: none;
}

.name:hover {
  text-decoration: underline;
}

.muted {
  color: var(--color-text-secondary);
}

.empty {
  padding: 32px;
  text-align: center;
  color: var(--color-text-secondary);
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.badge.active {
  border-color: var(--color-success);
  color: var(--color-success);
}

.badge.archived {
  color: var(--color-text-secondary);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  font-size: 13px;
}

.btn {
  height: 36px;
  padding: 0 16px;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: #fff;
}

.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.secondary {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}
</style>

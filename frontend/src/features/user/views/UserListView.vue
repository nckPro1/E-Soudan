<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { usePermission } from '../../../shared/composables/usePermission'
import { toApiError } from '../../../shared/api/http'
import { useAuthStore } from '../../auth/store'
import type { SystemRole } from '../../auth/types'
import * as userApi from '../api'
import UserFormDialog from '../components/UserFormDialog.vue'
import type { ManagedUser, PageResult, UserStatus } from '../types'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

const { t, te } = useI18n()
const auth = useAuthStore()
const { can } = usePermission()

const result = ref<PageResult<ManagedUser> | null>(null)
const loading = ref(false)
const errorCode = ref('')
const page = ref(0)
const search = ref('')
const status = ref<UserStatus | ''>('')
const role = ref<SystemRole | ''>('')

const formOpen = ref(false)
const editTarget = ref<ManagedUser | null>(null)
const confirmTarget = ref<{ user: ManagedUser; lock: boolean } | null>(null)
const acting = ref(false)

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

// Chống kết quả cũ ghi đè kết quả mới khi gõ tìm nhanh
let requestSeq = 0

async function load() {
  const seq = ++requestSeq
  loading.value = true
  errorCode.value = ''
  try {
    const data = await userApi.listUsers({
      page: page.value,
      size: PAGE_SIZE,
      q: search.value.trim(),
      status: status.value,
      role: role.value,
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
watch([status, role], () => {
  page.value = 0
  void load()
})
onMounted(load)
onUnmounted(() => clearTimeout(timer))

function goTo(target: number) {
  page.value = target
  void load()
}

function openCreate() {
  editTarget.value = null
  formOpen.value = true
}

function openEdit(user: ManagedUser) {
  editTarget.value = user
  formOpen.value = true
}

function onSaved() {
  formOpen.value = false
  void load()
}

async function runConfirmed() {
  if (!confirmTarget.value || acting.value) return
  const { user, lock } = confirmTarget.value
  acting.value = true
  try {
    await (lock ? userApi.lockUser(user.id) : userApi.unlockUser(user.id))
    confirmTarget.value = null
    await load()
  } catch (error) {
    errorCode.value = toApiError(error).code
    confirmTarget.value = null
  } finally {
    acting.value = false
  }
}

const isSelf = (user: ManagedUser) => user.id === auth.user?.id
</script>

<template>
  <div>
    <div class="head">
      <h1>{{ t('users.title') }}</h1>
      <button
        v-if="can('USER_CREATE')"
        type="button"
        class="btn primary"
        @click="openCreate"
      >
        {{ t('users.create') }}
      </button>
    </div>

    <div class="filters">
      <input
        v-model="search"
        type="search"
        class="control search"
        :placeholder="t('users.searchPlaceholder')"
        :aria-label="t('users.searchPlaceholder')"
      >
      <select
        v-model="role"
        class="control"
        :aria-label="t('users.role')"
      >
        <option value="">
          {{ t('users.allRoles') }}
        </option>
        <option value="ADMIN">
          {{ t('users.roles.ADMIN') }}
        </option>
        <option value="MANAGER">
          {{ t('users.roles.MANAGER') }}
        </option>
        <option value="USER">
          {{ t('users.roles.USER') }}
        </option>
      </select>
      <select
        v-model="status"
        class="control"
        :aria-label="t('users.status')"
      >
        <option value="">
          {{ t('users.allStatuses') }}
        </option>
        <option value="ACTIVE">
          {{ t('users.statuses.ACTIVE') }}
        </option>
        <option value="LOCKED">
          {{ t('users.statuses.LOCKED') }}
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
            <th>{{ t('users.fullName') }}</th>
            <th>{{ t('auth.email') }}</th>
            <th>{{ t('users.position') }}</th>
            <th>{{ t('users.role') }}</th>
            <th>{{ t('users.status') }}</th>
            <th class="right">
              {{ t('users.actions') }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in result?.items ?? []"
            :key="user.id"
          >
            <td>{{ user.fullName }}</td>
            <td>{{ user.email }}</td>
            <td>{{ user.position ?? '' }}</td>
            <td>{{ t(`users.roles.${user.systemRole}`) }}</td>
            <td>
              <span
                class="badge"
                :class="user.status === 'LOCKED' ? 'locked' : 'active'"
              >{{ t(`users.statuses.${user.status}`) }}</span>
            </td>
            <td class="right">
              <button
                v-if="can('USER_UPDATE')"
                type="button"
                class="link"
                @click="openEdit(user)"
              >
                {{ t('common.edit') }}
              </button>
              <button
                v-if="can('USER_LOCK') && user.status === 'ACTIVE' && !isSelf(user)"
                type="button"
                class="link danger"
                @click="confirmTarget = { user, lock: true }"
              >
                {{ t('users.lock') }}
              </button>
              <button
                v-if="can('USER_LOCK') && user.status === 'LOCKED'"
                type="button"
                class="link"
                @click="confirmTarget = { user, lock: false }"
              >
                {{ t('users.unlock') }}
              </button>
            </td>
          </tr>
          <tr v-if="!loading && result && result.items.length === 0">
            <td
              colspan="6"
              class="empty"
            >
              {{ t('users.empty') }}
            </td>
          </tr>
          <tr v-if="loading && !result">
            <td
              colspan="6"
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

    <UserFormDialog
      v-if="formOpen"
      :user="editTarget"
      @close="formOpen = false"
      @saved="onSaved"
    />

    <ModalDialog
      v-if="confirmTarget"
      :title="confirmTarget.lock ? t('users.lockTitle') : t('users.unlockTitle')"
      @close="confirmTarget = null"
    >
      <p class="confirm-text">
        {{ t(confirmTarget.lock ? 'users.lockConfirm' : 'users.unlockConfirm', { name: confirmTarget.user.fullName }) }}
      </p>
      <div class="buttons">
        <button
          type="button"
          class="btn secondary"
          :disabled="acting"
          @click="confirmTarget = null"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn primary"
          :disabled="acting"
          @click="runConfirmed"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
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
  white-space: nowrap;
}

th {
  background: var(--color-surface-muted);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

tbody tr:last-child td {
  border-bottom: 0;
}

.right {
  text-align: right;
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

.badge.locked {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.link {
  margin-left: 12px;
  padding: 0;
  background: none;
  border: 0;
  font-size: 13px;
  color: var(--color-primary);
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.link.danger {
  color: var(--color-danger);
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

.confirm-text {
  margin: 0 0 24px;
  font-size: 14px;
  line-height: 1.6;
}

.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>

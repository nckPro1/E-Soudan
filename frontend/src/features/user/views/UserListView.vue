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
    <div class="head mb-6 flex items-center justify-between">
      <h1 class="page-title">
        {{ t('users.title') }}
      </h1>
      <button
        v-if="can('USER_CREATE')"
        type="button"
        class="btn btn-primary"
        @click="openCreate"
      >
        {{ t('users.create') }}
      </button>
    </div>

    <div class="filters mb-4 flex flex-wrap gap-3">
      <input
        v-model="search"
        type="search"
        class="control search min-w-[12.5rem] max-w-[22.5rem] flex-1"
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
      <table class="table data-table">
        <thead>
          <tr>
            <th>{{ t('users.fullName') }}</th>
            <th>{{ t('auth.email') }}</th>
            <th>{{ t('users.position') }}</th>
            <th>{{ t('users.role') }}</th>
            <th>{{ t('users.status') }}</th>
            <th class="right !text-right">
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
                :class="user.status === 'LOCKED' ? 'locked badge-danger' : 'active badge-success'"
              >{{ t(`users.statuses.${user.status}`) }}</span>
            </td>
            <td class="right !text-right">
              <button
                v-if="can('USER_UPDATE')"
                type="button"
                class="link ml-3 cursor-pointer"
                @click="openEdit(user)"
              >
                {{ t('common.edit') }}
              </button>
              <button
                v-if="can('USER_LOCK') && user.status === 'ACTIVE' && !isSelf(user)"
                type="button"
                class="link danger ml-3 cursor-pointer !text-[#c4314b]"
                @click="confirmTarget = { user, lock: true }"
              >
                {{ t('users.lock') }}
              </button>
              <button
                v-if="can('USER_LOCK') && user.status === 'LOCKED'"
                type="button"
                class="link ml-3 cursor-pointer"
                @click="confirmTarget = { user, lock: false }"
              >
                {{ t('users.unlock') }}
              </button>
            </td>
          </tr>
          <tr v-if="!loading && result && result.items.length === 0">
            <td
              colspan="6"
              class="empty empty-cell"
            >
              {{ t('users.empty') }}
            </td>
          </tr>
          <tr v-if="loading && !result">
            <td
              colspan="6"
              class="empty empty-cell"
            >
              {{ t('common.loading') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="result && result.totalPages > 1"
      class="pager mt-4 flex items-center justify-center gap-4 text-[0.8125rem]"
    >
      <button
        type="button"
        class="btn"
        :disabled="page === 0 || loading"
        @click="goTo(page - 1)"
      >
        {{ t('common.prev') }}
      </button>
      <span>{{ t('common.pageOf', { page: page + 1, total: result.totalPages }) }}</span>
      <button
        type="button"
        class="btn"
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
      <p class="confirm-text mb-4 text-sm">
        {{ t(confirmTarget.lock ? 'users.lockConfirm' : 'users.unlockConfirm', { name: confirmTarget.user.fullName }) }}
      </p>
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="acting"
          @click="confirmTarget = null"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="acting"
          @click="runConfirmed"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

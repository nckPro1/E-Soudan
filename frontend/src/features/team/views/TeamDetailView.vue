<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { usePermission } from '../../../shared/composables/usePermission'
import { toApiError } from '../../../shared/api/http'
import * as teamApi from '../api'
import TeamFormDialog from '../components/TeamFormDialog.vue'
import UserPicker from '../components/UserPicker.vue'
import type { TeamDetail, TeamMemberInfo, TeamRole } from '../types'

const props = defineProps<{ id: number }>()

const { t, te } = useI18n()
const { can } = usePermission()

const team = ref<TeamDetail | null>(null)
const loading = ref(true)
const errorCode = ref('')
const acting = ref(false)

const editing = ref(false)
const adding = ref(false)
const addRole = ref<TeamRole>('MEMBER')
const addUser = ref<number[]>([])
const removeTarget = ref<TeamMemberInfo | null>(null)
const archiveOpen = ref(false)

const canManage = computed(() => can('TEAM_MANAGE') && team.value?.status === 'ACTIVE')
const existingIds = computed(() => team.value?.members.map((m) => m.userId) ?? [])
const pickerRole = computed(() => (addRole.value === 'OBSERVER' ? 'MANAGER' : 'USER'))

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function load() {
  loading.value = true
  errorCode.value = ''
  try {
    team.value = await teamApi.getTeam(props.id)
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** Chạy một thao tác sửa nhóm; thành công thì dùng ngay dữ liệu mới server trả về. */
async function run(action: () => Promise<TeamDetail>): Promise<boolean> {
  if (acting.value) return false
  acting.value = true
  errorCode.value = ''
  try {
    team.value = await action()
    return true
  } catch (error) {
    errorCode.value = toApiError(error).code
    return false
  } finally {
    acting.value = false
  }
}

async function changeRole(member: TeamMemberInfo, role: TeamRole) {
  if (role === member.teamRole) return
  const ok = await run(() => teamApi.changeMemberRole(props.id, member.userId, role))
  if (!ok) {
    // Tải lại để ô chọn trở về giá trị thật, nhưng giữ thông báo lỗi cho người dùng đọc
    const code = errorCode.value
    await load()
    errorCode.value = code
  }
}

async function confirmRemove() {
  const target = removeTarget.value
  if (!target) return
  await run(() => teamApi.removeMember(props.id, target.userId))
  removeTarget.value = null
}

async function confirmAdd() {
  if (addUser.value.length === 0) return
  if (await run(() => teamApi.addMember(props.id, addUser.value[0], addRole.value))) {
    adding.value = false
    addUser.value = []
  }
}

async function confirmArchive() {
  if (!team.value) return
  const version = team.value.version
  await run(() => teamApi.archiveTeam(props.id, version))
  archiveOpen.value = false
}

function openAdd() {
  addRole.value = 'MEMBER'
  addUser.value = []
  errorCode.value = ''
  adding.value = true
}

function onSaved(saved: TeamDetail) {
  team.value = saved
  editing.value = false
}

const roleOrder: TeamRole[] = ['LEADER', 'MEMBER', 'OBSERVER']
const assignable = (member: TeamMemberInfo): TeamRole[] =>
  member.teamRole === 'OBSERVER' ? ['OBSERVER'] : roleOrder.filter((r) => r !== 'OBSERVER')
</script>

<template>
  <div>
    <RouterLink
      :to="{ name: 'teams' }"
      class="back"
    >
      ← {{ t('team.title') }}
    </RouterLink>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <p
      v-if="loading"
      class="muted"
    >
      {{ t('common.loading') }}
    </p>

    <template v-else-if="team">
      <div class="head">
        <div>
          <h1>{{ team.name }}</h1>
          <p
            v-if="team.description"
            class="muted"
          >
            {{ team.description }}
          </p>
        </div>
        <div class="head-actions">
          <span
            class="badge"
            :class="team.status === 'ARCHIVED' ? 'archived' : 'active'"
          >{{ t(`team.statuses.${team.status}`) }}</span>
          <template v-if="canManage">
            <button
              type="button"
              class="btn secondary"
              @click="editing = true"
            >
              {{ t('common.edit') }}
            </button>
            <button
              type="button"
              class="btn danger"
              @click="archiveOpen = true"
            >
              {{ t('team.archive') }}
            </button>
          </template>
        </div>
      </div>

      <AlertBanner
        v-if="team.status === 'ARCHIVED'"
        tone="info"
      >
        {{ t('team.archivedNotice') }}
      </AlertBanner>

      <div class="section-head">
        <h2>{{ t('team.members') }} ({{ team.members.length }})</h2>
        <button
          v-if="canManage"
          type="button"
          class="btn primary"
          @click="openAdd"
        >
          {{ t('team.addMember') }}
        </button>
      </div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>{{ t('users.fullName') }}</th>
              <th>{{ t('users.position') }}</th>
              <th>{{ t('team.teamRole') }}</th>
              <th v-if="canManage" />
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="member in team.members"
              :key="member.userId"
            >
              <td>{{ member.fullName }}</td>
              <td class="muted">
                {{ member.position ?? '' }}
              </td>
              <td>
                <select
                  v-if="canManage"
                  class="role-select"
                  :value="member.teamRole"
                  :disabled="acting"
                  :aria-label="`${t('team.teamRole')} ${member.fullName}`"
                  @change="changeRole(member, ($event.target as HTMLSelectElement).value as TeamRole)"
                >
                  <option
                    v-for="role in assignable(member)"
                    :key="role"
                    :value="role"
                  >
                    {{ t(`team.roles.${role}`) }}
                  </option>
                </select>
                <template v-else>
                  {{ t(`team.roles.${member.teamRole}`) }}
                </template>
              </td>
              <td
                v-if="canManage"
                class="right"
              >
                <button
                  type="button"
                  class="link danger"
                  :disabled="acting"
                  @click="removeTarget = member"
                >
                  {{ t('team.remove') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <TeamFormDialog
      v-if="editing && team"
      :team="team"
      @close="editing = false"
      @saved="onSaved"
    />

    <ModalDialog
      v-if="adding"
      :title="t('team.addMember')"
      wide
      @close="adding = false"
    >
      <AlertBanner v-if="errorMessage">
        {{ errorMessage }}
      </AlertBanner>
      <div class="add-form">
        <div>
          <label
            for="add-role"
            class="label"
          >{{ t('team.teamRole') }}</label>
          <select
            id="add-role"
            v-model="addRole"
            class="role-select full"
            @change="addUser = []"
          >
            <option value="LEADER">
              {{ t('team.roles.LEADER') }}
            </option>
            <option value="MEMBER">
              {{ t('team.roles.MEMBER') }}
            </option>
            <option value="OBSERVER">
              {{ t('team.roles.OBSERVER') }}
            </option>
          </select>
        </div>
        <UserPicker
          :key="pickerRole"
          v-model="addUser"
          :label="t('team.pickPerson')"
          :role="pickerRole"
          :excluded="existingIds"
          single
        />
        <div class="buttons">
          <button
            type="button"
            class="btn secondary"
            :disabled="acting"
            @click="adding = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="btn primary"
            :disabled="acting || addUser.length === 0"
            @click="confirmAdd"
          >
            {{ t('common.save') }}
          </button>
        </div>
      </div>
    </ModalDialog>

    <ModalDialog
      v-if="removeTarget"
      :title="t('team.removeTitle')"
      @close="removeTarget = null"
    >
      <p class="confirm-text">
        {{ t('team.removeConfirm', { name: removeTarget.fullName }) }}
      </p>
      <div class="buttons">
        <button
          type="button"
          class="btn secondary"
          :disabled="acting"
          @click="removeTarget = null"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn primary"
          :disabled="acting"
          @click="confirmRemove"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>

    <ModalDialog
      v-if="archiveOpen && team"
      :title="t('team.archive')"
      @close="archiveOpen = false"
    >
      <p class="confirm-text">
        {{ t('team.archiveConfirm', { name: team.name }) }}
      </p>
      <div class="buttons">
        <button
          type="button"
          class="btn secondary"
          :disabled="acting"
          @click="archiveOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn primary"
          :disabled="acting"
          @click="confirmArchive"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--color-primary);
  text-decoration: none;
}

.back:hover {
  text-decoration: underline;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

h1 {
  margin: 0 0 4px;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 500;
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
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

.role-select {
  height: 32px;
  padding: 0 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--color-text);
}

.role-select.full {
  width: 100%;
  height: 40px;
  font-size: 14px;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}

.add-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.link {
  padding: 0;
  background: none;
  border: 0;
  font-size: 13px;
  color: var(--color-primary);
  cursor: pointer;
}

.link.danger {
  color: var(--color-danger);
}

.link:hover:not(:disabled) {
  text-decoration: underline;
}

.link:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.danger {
  background: var(--color-surface);
  border: 1px solid var(--color-danger);
  color: var(--color-danger);
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

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { usePermission } from '../../../shared/composables/usePermission'
import { toApiError } from '../../../shared/api/http'
import * as teamApi from '../api'
import TeamFormDialog from '../components/TeamFormDialog.vue'
import UserPicker from '../components/UserPicker.vue'
import TeamTasks from '../../task/components/TeamTasks.vue'
import { useAuthStore } from '../../auth/store'
import * as aiApi from '../../ai/api'
import AiSummaryDialog from '../../ai/components/AiSummaryDialog.vue'
import { useAiLang } from '../../ai/useAiCall'
import ChatPane from '../../chat/components/ChatPane.vue'
import { useChatStore } from '../../chat/store'
import type { Conversation } from '../../chat/types'
import { useProfileStore } from '../../people/profile'
import type { TeamDetail, TeamMemberInfo, TeamRole } from '../types'

type Tab = 'chat' | 'members' | 'tasks'

/**
 * Một nhóm, bố cục như Teams: tiêu đề + thao tác quản lý, rồi các tab Trò chuyện (phòng chat của nhóm, chỉ người không
 * phải Admin), Thành viên, Công việc. Tab nằm trên đường dẫn (?tab=) để tải lại trang vẫn giữ nguyên.
 */
const props = defineProps<{ id: number; tab?: string }>()

const { t, te } = useI18n()
const { can, isAdmin } = usePermission()
const auth = useAuthStore()
const chat = useChatStore()
const profile = useProfileStore()
const router = useRouter()
const aiSummary = ref(false)
const aiLang = useAiLang()

const tabs = computed<Tab[]>(() => (isAdmin.value ? ['members', 'tasks'] : ['chat', 'members', 'tasks']))
const activeTab = computed<Tab>(() => {
  const wanted = props.tab as Tab | undefined
  return wanted && tabs.value.includes(wanted) ? wanted : tabs.value[0]
})
function selectTab(tab: Tab) {
  void router.replace({ query: { tab } })
}

/** Phòng chat của nhóm: chỉ tải khi mở tab Trò chuyện lần đầu. */
const room = ref<Conversation | null>(null)
const roomError = ref('')
watch(
  activeTab,
  async (tab) => {
    if (tab !== 'chat' || room.value) return
    try {
      room.value = await chat.openTeamRoom(props.id)
    } catch (error) {
      roomError.value = toApiError(error).code
    }
  },
  { immediate: true },
)

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

/** Leader của nhóm này (không phải Admin) mới giao được việc, và chỉ khi nhóm còn hoạt động. */
const isLeader = computed(
  () => team.value?.members.some((m) => m.userId === auth.user?.id && m.teamRole === 'LEADER') ?? false,
)
const canAssign = computed(() => isLeader.value && team.value?.status === 'ACTIVE')
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
  <div class="flex h-full min-h-0 flex-col">
    <AlertBanner
      v-if="errorMessage"
      class="m-4"
    >
      {{ errorMessage }}
    </AlertBanner>

    <p
      v-if="loading"
      class="muted p-6 text-[0.8125rem] text-[#616161]"
    >
      {{ t('common.loading') }}
    </p>

    <template v-else-if="team">
      <header class="shrink-0 border-b border-[#e1dfdd] px-6 pt-3 max-md:px-3">
        <div class="head flex items-center justify-between gap-3">
          <div class="flex min-w-0 items-center gap-2">
            <RouterLink
              :to="{ name: 'teams' }"
              class="back rounded p-1 text-[#616161] hover:bg-black/5 md:hidden"
              :aria-label="t('team.title')"
            >
              ←
            </RouterLink>
            <div class="min-w-0">
              <h1 class="truncate text-lg font-bold">
                {{ team.name }}
              </h1>
              <p
                v-if="team.description"
                class="muted truncate text-xs text-[#616161]"
              >
                {{ team.description }}
              </p>
            </div>
          </div>
          <div class="head-actions flex shrink-0 items-center gap-2">
            <button
              v-if="activeTab === 'chat' && room"
              type="button"
              class="flex items-center gap-1 rounded px-2 py-1 text-xs font-semibold text-[#4f52b2] hover:bg-[#4f52b2]/10"
              :title="t('ai.summarizeShort')"
              data-testid="ai-summary-button"
              @click="aiSummary = true"
            >
              <span aria-hidden="true">✦</span>{{ t('ai.summarizeShort') }}
            </button>
            <span
              class="badge"
              :class="team.status === 'ARCHIVED' ? 'archived' : 'active badge-success'"
            >{{ t(`team.statuses.${team.status}`) }}</span>
            <template v-if="canManage">
              <button
                type="button"
                class="btn"
                @click="editing = true"
              >
                {{ t('common.edit') }}
              </button>
              <button
                type="button"
                class="btn btn-danger"
                @click="archiveOpen = true"
              >
                {{ t('team.archive') }}
              </button>
            </template>
          </div>
        </div>

        <nav
          class="mt-2 flex gap-4 text-[0.8125rem]"
          role="tablist"
        >
          <button
            v-for="tabName in tabs"
            :key="tabName"
            type="button"
            role="tab"
            class="-mb-px border-b-2 px-1 pb-2 pt-1 font-semibold"
            :class="activeTab === tabName ? 'border-[#4f52b2] text-[#4f52b2]' : 'border-transparent text-[#616161] hover:text-[#242424]'"
            :aria-selected="activeTab === tabName"
            :data-testid="`tab-${tabName}`"
            @click="selectTab(tabName)"
          >
            {{ t(`team.tab${tabName[0].toUpperCase()}${tabName.slice(1)}`) }}
          </button>
        </nav>
      </header>

      <AlertBanner
        v-if="team.status === 'ARCHIVED'"
        tone="info"
        class="mx-6 mt-3"
      >
        {{ t('team.archivedNotice') }}
      </AlertBanner>

      <!-- Tab Trò chuyện: phòng chat của nhóm -->
      <div
        v-if="activeTab === 'chat'"
        class="min-h-0 flex-1"
        data-testid="team-chat"
      >
        <ChatPane
          v-if="room"
          :conversation="room"
        />
        <p
          v-else-if="roomError"
          class="p-6 text-sm text-[#c4314b]"
        >
          {{ te(`errors.${roomError}`) ? t(`errors.${roomError}`) : t('errors.UNKNOWN') }}
        </p>
        <p
          v-else
          class="p-6 text-[0.8125rem] text-[#616161]"
        >
          {{ t('common.loading') }}
        </p>
      </div>

      <div
        v-else
        class="min-h-0 flex-1 overflow-auto px-6 py-4 max-md:px-3"
      >
        <template v-if="activeTab === 'members'">
          <div class="section-head mb-3 flex items-center justify-between">
            <h2 class="text-base font-bold">
              {{ t('team.members') }} ({{ team.members.length }})
            </h2>
            <button
              v-if="canManage"
              type="button"
              class="btn btn-primary"
              @click="openAdd"
            >
              {{ t('team.addMember') }}
            </button>
          </div>

          <div class="table-wrap">
            <table class="table data-table">
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
                  <td>
                    <button
                      type="button"
                      class="link cursor-pointer"
                      data-testid="member-name"
                      @click="profile.open(member.userId)"
                    >
                      {{ member.fullName }}
                    </button>
                  </td>
                  <td class="muted text-[0.8125rem] text-[#616161]">
                    {{ member.position ?? '' }}
                  </td>
                  <td>
                    <select
                      v-if="canManage"
                      class="role-select control !h-8"
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
                    class="right !text-right"
                  >
                    <button
                      type="button"
                      class="link danger cursor-pointer !text-[#c4314b]"
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

        <template v-else>
          <TeamTasks
            :team-id="team.id"
            :members="team.members"
            :can-assign="canAssign"
          />
        </template>
      </div>
    </template>

    <AiSummaryDialog
      v-if="aiSummary && room"
      :title="`${t('ai.summarizeShort')}: ${team?.name ?? ''}`"
      :call="() => aiApi.summarizeChat(room!.id, 24, aiLang)"
      @close="aiSummary = false"
    />

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
      <div class="add-form flex flex-col gap-4">
        <div>
          <label
            for="add-role"
            class="label field-label"
          >{{ t('team.teamRole') }}</label>
          <select
            id="add-role"
            v-model="addRole"
            class="role-select control w-full"
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
        <div class="buttons mt-2 flex justify-end gap-2">
          <button
            type="button"
            class="btn"
            :disabled="acting"
            @click="adding = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="btn btn-primary"
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
      <p class="confirm-text mb-4 text-sm">
        {{ t('team.removeConfirm', { name: removeTarget.fullName }) }}
      </p>
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="acting"
          @click="removeTarget = null"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
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
      <p class="confirm-text mb-4 text-sm">
        {{ t('team.archiveConfirm', { name: team.name }) }}
      </p>
      <div class="buttons mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="btn"
          :disabled="acting"
          @click="archiveOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="acting"
          @click="confirmArchive"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

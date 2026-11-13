<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { colorOf, tileLabelOf } from '../../../shared/utils/avatar'
import { useAuthStore } from '../../auth/store'
import CommunityFormDialog from '../components/CommunityFormDialog.vue'
import { useCommunityStore } from '../store'
import type { CommunityDetail } from '../types'

/**
 * App Báo cáo, bố cục như màn Chat của Teams. Cột trái dùng nguyên markup "ChatListSidebar" của bản thiết kế Stitch
 * (design/homepage-assets/teams.html): tiêu đề + icon, chip lọc, các mục có thể thu gọn. Khung phải là feed của
 * cộng đồng đang chọn. Manager thấy cộng đồng mình tạo và có nút tạo mới; Leader/Member chỉ thấy cộng đồng mình
 * được thêm vào. Màn hình hẹp chỉ hiện một cột tại một thời điểm.
 */
const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useCommunityStore()

const creating = ref(false)
const collapsed = ref(false)
const searching = ref(false)
const query = ref('')
/** Chip lọc như "Unread / Meeting chats" của Teams: chỉ cộng đồng mình quản lý, hoặc chỉ cộng đồng mình tham gia. */
const chip = ref<'owned' | 'joined' | null>(null)

const isManager = computed(() => auth.user?.systemRole === 'MANAGER')
const visible = computed(() => {
  const text = query.value.trim().toLowerCase()
  return store.sorted.filter(
    (c) =>
      (!text || c.name.toLowerCase().includes(text)) &&
      (chip.value === null || (chip.value === 'owned' ? c.owned : !c.owned)),
  )
})
const selectedId = computed(() => (route.params.id ? Number(route.params.id) : null))
/** Thành viên đang được xem báo cáo (nhãn tên được chọn); null khi đang ở trang tổng quan cộng đồng. */
const selectedUserId = computed(() => (route.params.userId ? Number(route.params.userId) : null))
const myId = computed(() => auth.user?.id)

/** Cộng đồng đang mở danh sách nhãn tên thành viên ở sidebar. */
const expanded = ref<Set<number>>(new Set())

function expand(id: number) {
  expanded.value = new Set(expanded.value).add(id)
  void store.loadMembers(id)
}

function toggleExpand(id: number) {
  if (expanded.value.has(id)) {
    const next = new Set(expanded.value)
    next.delete(id)
    expanded.value = next
  } else {
    expand(id)
  }
}

/** Nhãn tên của một cộng đồng: mình đứng đầu, rồi theo thứ tự chữ cái. */
function labelsOf(communityId: number) {
  const list = store.members[communityId] ?? []
  return [...list].sort((a, b) => {
    if (a.id === myId.value) return -1
    if (b.id === myId.value) return 1
    return a.fullName.localeCompare(b.fullName)
  })
}

// Vào một cộng đồng (hoặc một trang thành viên) thì tự mở danh sách nhãn của cộng đồng đó
watch(selectedId, (id) => id !== null && expand(id), { immediate: true })
const errorMessage = computed(() => {
  if (!store.errorCode) return ''
  return te(`errors.${store.errorCode}`) ? t(`errors.${store.errorCode}`) : t('errors.UNKNOWN')
})

function toggleChip(value: 'owned' | 'joined') {
  chip.value = chip.value === value ? null : value
}

onMounted(() => {
  void store.load()
})

async function onCreated(detail: CommunityDetail) {
  creating.value = false
  store.upsert(detail.community)
  await router.push({ name: 'community-feed', params: { id: detail.community.id } })
}
</script>

<template>
  <div class="flex h-full">
    <!-- BEGIN: ChatListSidebar (markup Stitch) -->
    <aside
      class="w-[17.5rem] bg-[#f5f5f5] flex flex-col border-r border-[#e0e0e0] shrink-0 max-md:w-full max-md:border-r-0"
      :class="{ 'max-md:hidden': selectedId !== null }"
      :aria-label="t('report.communities')"
      data-purpose="chat-sidebar"
    >
      <!-- Sidebar Header: Title & Action buttons -->
      <div class="px-4 pt-3 pb-2 flex items-center justify-between">
        <h1 class="text-xl font-bold text-[#242424]">
          {{ t('report.communities') }}
        </h1>
        <div class="flex items-center space-x-1 text-[#616161]">
          <button
            type="button"
            class="p-1 rounded hover:bg-black/5"
            :class="{ 'bg-[#4f52b2]/10 text-[#4f52b2]': searching }"
            :title="t('report.searchCommunities')"
            :aria-label="t('report.searchCommunities')"
            :aria-pressed="searching"
            data-testid="search-communities"
            @click="searching = !searching"
          >
            <svg
              class="w-4 h-4"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            ><path
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              stroke-linecap="round"
              stroke-linejoin="round"
            /></svg>
          </button>
          <button
            v-if="isManager"
            type="button"
            class="p-1 rounded hover:bg-black/5"
            :title="t('report.createCommunity')"
            :aria-label="t('report.createCommunity')"
            data-testid="create-community"
            @click="creating = true"
          >
            <svg
              class="w-4 h-4"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            ><path
              d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
              stroke-linecap="round"
              stroke-linejoin="round"
            /></svg>
          </button>
        </div>
      </div>

      <div
        v-if="searching"
        class="px-4 pb-1"
      >
        <input
          v-model="query"
          type="search"
          class="w-full h-7 px-2.5 text-xs bg-white border border-[#e1dfdd] rounded outline-none focus:border-[#5c5fc8]"
          :placeholder="t('report.searchCommunities')"
          :aria-label="t('report.searchCommunities')"
          data-testid="community-filter"
        >
      </div>

      <!-- Filter Chips -->
      <div class="px-4 py-1.5 flex items-center space-x-1.5 text-xs text-[#424242]">
        <button
          type="button"
          class="px-2.5 py-1 rounded-full bg-white border border-[#e1dfdd] hover:bg-black/5"
          :class="{ '!border-[#4f52b2] !text-[#4f52b2] !bg-[#4f52b2]/10': chip === 'owned' }"
          :aria-pressed="chip === 'owned'"
          @click="toggleChip('owned')"
        >
          {{ t('report.filterOwned') }}
        </button>
        <button
          type="button"
          class="px-2.5 py-1 rounded-full bg-white border border-[#e1dfdd] hover:bg-black/5"
          :class="{ '!border-[#4f52b2] !text-[#4f52b2] !bg-[#4f52b2]/10': chip === 'joined' }"
          :aria-pressed="chip === 'joined'"
          @click="toggleChip('joined')"
        >
          {{ t('report.filterJoined') }}
        </button>
      </div>

      <AlertBanner v-if="errorMessage">
        {{ errorMessage }}
      </AlertBanner>

      <!-- Chat List Groups Scrollable Area -->
      <div class="flex-1 overflow-y-auto px-2 pt-2 space-y-3">
        <div>
          <button
            type="button"
            class="flex items-center px-2 py-1 text-xs font-semibold text-[#616161] cursor-pointer w-full text-left"
            :aria-expanded="!collapsed"
            @click="collapsed = !collapsed"
          >
            <svg
              class="w-3 h-3 mr-1 text-[#616161] transition-transform"
              :class="{ '-rotate-90': collapsed }"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            ><path
              d="M19 9l-7 7-7-7"
              stroke-linecap="round"
              stroke-linejoin="round"
            /></svg>
            <span>{{ t('report.communities') }}</span>
          </button>

          <p
            v-if="store.loading && !store.loaded"
            class="px-2 py-2 text-xs text-[#616161]"
          >
            {{ t('common.loading') }}
          </p>
          <p
            v-else-if="store.loaded && store.items.length === 0"
            class="px-2 py-2 text-xs text-[#616161]"
            data-testid="empty"
          >
            {{ isManager ? t('report.emptyManager') : t('report.emptyMember') }}
          </p>

          <template v-if="!collapsed">
            <div
              v-for="c in visible"
              :key="c.id"
              class="community"
            >
              <div class="flex items-center">
                <!-- Mũi tên mở/đóng danh sách nhãn tên thành viên -->
                <button
                  type="button"
                  class="mr-0.5 mt-1 flex h-6 w-5 shrink-0 items-center justify-center rounded text-[#616161] hover:bg-black/5"
                  :aria-expanded="expanded.has(c.id)"
                  :aria-label="expanded.has(c.id) ? t('report.collapse') : t('report.expand')"
                  :title="expanded.has(c.id) ? t('report.collapse') : t('report.expand')"
                  :data-testid="`toggle-members-${c.id}`"
                  @click="toggleExpand(c.id)"
                >
                  <svg
                    class="h-3 w-3 transition-transform"
                    :class="{ '-rotate-90': !expanded.has(c.id) }"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  ><path d="M19 9l-7 7-7-7" /></svg>
                </button>

                <RouterLink
                  :to="{ name: 'community-feed', params: { id: c.id } }"
                  data-testid="community-item"
                  class="min-w-0 flex-1"
                  :class="
                    selectedId === c.id && selectedUserId === null
                      ? 'flex items-center justify-between px-2 py-2 rounded bg-white shadow-sm border border-[#e1dfdd] cursor-pointer mt-0.5 group'
                      : 'flex items-center px-2 py-2 rounded hover:bg-[#edebe9] cursor-pointer mt-1'
                  "
                >
                  <div
                    class="w-8 h-8 rounded-full shrink-0 flex items-center justify-center text-white text-xs font-semibold mr-2.5"
                    :style="{ background: colorOf(c.name) }"
                    aria-hidden="true"
                  >
                    {{ tileLabelOf(c.name) }}
                  </div>
                  <div class="min-w-0 flex-1">
                    <div class="truncate text-xs">
                      <span :class="selectedId === c.id ? 'font-semibold' : 'font-medium'">{{ c.name }}</span>
                    </div>
                    <p class="text-[0.6875rem] text-[#616161] italic truncate">
                      {{ t('report.memberCount', { count: c.memberCount }) }}<template v-if="c.owner && !c.owned"> · {{ c.owner.fullName }}</template>
                    </p>
                  </div>
                </RouterLink>
              </div>

              <!-- Nhãn theo tên từng thành viên: bấm vào thì mới thấy báo cáo riêng của người đó -->
              <ul
                v-if="expanded.has(c.id)"
                class="member-labels ml-6 mt-0.5 border-l border-[#e1dfdd] pl-2"
                :aria-label="t('report.membersTitle')"
              >
                <li
                  v-if="!store.members[c.id]"
                  class="px-2 py-1 text-[0.6875rem] text-[#616161]"
                >
                  {{ t('common.loading') }}
                </li>
                <li
                  v-else-if="labelsOf(c.id).length === 0"
                  class="px-2 py-1 text-[0.6875rem] text-[#616161]"
                >
                  {{ t('report.noMembersYet') }}
                </li>
                <li
                  v-for="m in labelsOf(c.id)"
                  :key="m.id"
                >
                  <RouterLink
                    :to="{ name: 'member-reports', params: { id: c.id, userId: m.id } }"
                    class="member-label flex items-center gap-2 rounded px-2 py-1.5 text-xs"
                    :class="
                      selectedId === c.id && selectedUserId === m.id
                        ? 'bg-white font-semibold shadow-sm ring-1 ring-[#e1dfdd]'
                        : 'text-[#242424] hover:bg-[#edebe9]'
                    "
                    :aria-current="selectedId === c.id && selectedUserId === m.id ? 'page' : undefined"
                    data-testid="member-label"
                  >
                    <UserAvatar
                      :name="m.fullName"
                      :size="22"
                    />
                    <span class="min-w-0 flex-1 truncate">{{ m.fullName }}</span>
                    <span
                      v-if="m.id === myId"
                      class="shrink-0 rounded bg-[#4f52b2]/10 px-1.5 py-px text-[0.625rem] font-semibold text-[#4f52b2]"
                    >{{ t('report.you') }}</span>
                  </RouterLink>
                </li>
              </ul>
            </div>
          </template>
        </div>
      </div>
    </aside>
    <!-- END: ChatListSidebar -->

    <section
      class="flex-1 min-w-0 bg-white overflow-hidden"
      :class="{ 'max-md:hidden': selectedId === null }"
    >
      <!-- key theo đường dẫn: đổi cộng đồng thì nạp lại feed của cộng đồng mới -->
      <RouterView :key="route.fullPath" />
    </section>

    <CommunityFormDialog
      v-if="creating"
      :community="null"
      @close="creating = false"
      @saved="onCreated"
    />
  </div>
</template>

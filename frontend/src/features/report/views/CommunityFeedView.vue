<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import MenuButton, { type MenuItem } from '../../../shared/components/MenuButton.vue'
import * as aiApi from '../../ai/api'
import AiSummaryDialog from '../../ai/components/AiSummaryDialog.vue'
import { useAiLang } from '../../ai/useAiCall'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import { toApiError } from '../../../shared/api/http'
import { colorOf, tileLabelOf } from '../../../shared/utils/avatar'
import { useAuthStore } from '../../auth/store'
import * as reportApi from '../api'
import CommunityFormDialog from '../components/CommunityFormDialog.vue'
import DayOffDialog from '../components/DayOffDialog.vue'
import MembersDialog from '../components/MembersDialog.vue'
import PostCard from '../components/PostCard.vue'
import ReportComposer from '../components/ReportComposer.vue'
import { useCommunityStore } from '../store'
import type { CommunityDetail, PendingReport, ReportPost } from '../types'

/**
 * Khung phải của app Báo cáo, dùng nguyên markup "MainChatView" của bản thiết kế Stitch
 * (design/homepage-assets/teams.html): header, dải thông tin, dòng báo cáo xếp từ cũ (trên) tới mới (dưới) có
 * vạch ngày, và ô viết báo cáo ở đáy. Thành viên (Leader/Member) viết và đọc; chủ cộng đồng (Manager) đọc, nhận
 * xét, thấy ai chưa nộp và quản lý thành viên.
 */
/** {@code userId} có: trang báo cáo riêng của thành viên đó. Không có: tổng quan cộng đồng (danh sách thành viên). */
const props = defineProps<{ id: number; userId?: number }>()

const PAGE_SIZE = 10
const { t, te, locale } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const store = useCommunityStore()

const detail = ref<CommunityDetail | null>(null)
/** Mới nhất trước (theo thứ tự server); hiển thị thì đảo lại để cũ ở trên. */
const posts = ref<ReportPost[]>([])
const myToday = ref<ReportPost | null>(null)
const pending = ref<PendingReport | null>(null)
const page = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const errorCode = ref('')
const editingCommunity = ref(false)
const managingMembers = ref(false)
const confirmDelete = ref(false)
const showPending = ref(false)
/** Đang mở form viết báo cáo (phủ kín khung bên phải) */
const composing = ref(false)
const scroller = ref<HTMLElement | null>(null)

const community = computed(() => detail.value?.community ?? null)
const aiSummary = ref(false)
const dayOffOpen = ref(false)
const aiLang = useAiLang()
const isOwner = computed(() => community.value?.owned === true)
const isMember = computed(() => !isOwner.value && auth.user?.systemRole === 'USER')
/** Đang xem trang báo cáo của chính mình: chỉ ở trang này mới đăng và sửa được. */
const isMine = computed(() => props.userId !== undefined && props.userId === auth.user?.id)
/** Thành viên đang được xem (lấy từ danh sách thành viên của cộng đồng). */
const viewed = computed(() => detail.value?.members.find((m) => m.id === props.userId) ?? null)
const myId = computed(() => auth.user?.id)
const hasMore = computed(() => page.value + 1 < totalPages.value)
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

/** Báo cáo từ cũ tới mới, kèm vạch ngày chen vào khi sang ngày khác. */
const timeline = computed(() => {
  const ordered = [...posts.value].reverse()
  const rows: Array<{ kind: 'day'; key: string; label: string } | { kind: 'post'; key: string; post: ReportPost }> = []
  let last = ''
  for (const post of ordered) {
    if (post.reportDate !== last) {
      last = post.reportDate
      rows.push({ kind: 'day', key: `d-${post.reportDate}`, label: dayLabel(post.reportDate) })
    }
    rows.push({ kind: 'post', key: `p-${post.id}`, post })
  }
  return rows
})

function dayLabel(date: string): string {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(locale.value, {
    weekday: 'long',
    day: 'numeric',
    month: 'numeric',
    year: 'numeric',
  })
}

function scrollToBottom() {
  void nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}

async function loadFeed(target: number) {
  const result = await reportApi.listPosts(props.id, props.userId as number, target, PAGE_SIZE)
  posts.value = target === 0 ? result.items : [...posts.value, ...result.items]
  page.value = result.page
  totalPages.value = result.totalPages
}

async function loadPending() {
  pending.value = isOwner.value ? await reportApi.getPending(props.id) : null
}

onMounted(async () => {
  try {
    detail.value = await reportApi.getCommunity(props.id)
    store.upsert(detail.value.community)
    store.setMembers(props.id, detail.value.members)
    const tasks: Promise<unknown>[] = [loadPending()]
    // Báo cáo chỉ tải khi mở trang của một thành viên; tổng quan chỉ là danh sách tên
    if (props.userId !== undefined) tasks.push(loadFeed(0))
    if (isMember.value && isMine.value) tasks.push(reportApi.getMyToday(props.id).then((p) => (myToday.value = p)))
    await Promise.all(tasks)
    scrollToBottom()
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    loading.value = false
  }
})

/** Tải các báo cáo cũ hơn và giữ nguyên vị trí đang đọc (không nhảy cuộn). */
async function older() {
  if (loadingMore.value) return
  loadingMore.value = true
  const el = scroller.value
  const before = el?.scrollHeight ?? 0
  try {
    await loadFeed(page.value + 1)
    await nextTick()
    if (el) el.scrollTop += el.scrollHeight - before
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    loadingMore.value = false
  }
}

/** Sau khi nộp hoặc cập nhật báo cáo hôm nay: nạp lại từ đầu để bài hiện đúng chỗ, rồi cuộn xuống đáy. */
async function onSaved(post: ReportPost) {
  composing.value = false
  myToday.value = post
  try {
    await loadFeed(0)
    scrollToBottom()
  } catch (error) {
    errorCode.value = toApiError(error).code
  }
}

function replacePost(updated: ReportPost) {
  posts.value = posts.value.map((p) => (p.id === updated.id ? updated : p))
  if (myToday.value?.id === updated.id) myToday.value = updated
}

function onCommunityChanged(updated: CommunityDetail) {
  detail.value = updated
  store.upsert(updated.community)
  store.setMembers(props.id, updated.members)
  void loadPending()
}

function onEdited(updated: CommunityDetail) {
  editingCommunity.value = false
  detail.value = updated
  store.upsert(updated.community)
  store.setMembers(props.id, updated.members)
}

async function removeCommunity() {
  try {
    await reportApi.deleteCommunity(props.id)
    store.remove(props.id)
    await router.replace({ name: 'communities' })
  } catch (error) {
    confirmDelete.value = false
    errorCode.value = toApiError(error).code
  }
}

const menuItems = computed<MenuItem[]>(() => [
  { key: 'members', label: t('report.manageMembers') },
  { key: 'edit', label: t('common.edit') },
  { key: 'delete', label: t('report.deleteCommunity'), danger: true },
])

function onMenu(key: string) {
  if (key === 'members') managingMembers.value = true
  else if (key === 'edit') editingCommunity.value = true
  else if (key === 'delete') confirmDelete.value = true
}

function openComposer() {
  composing.value = true
}
</script>

<template>
  <!-- BEGIN: MainChatView (markup Stitch) -->
  <main
    class="h-full bg-white flex flex-col overflow-hidden"
    data-purpose="chat-main"
  >
    <!-- Form viết báo cáo: phủ kín cả khung -->
    <ReportComposer
      v-if="composing && community"
      :community-id="id"
      :today="myToday"
      @saved="onSaved"
      @close="composing = false"
    />

    <template v-else>
      <!-- Header of Active Chat -->
      <div
        v-if="community"
        class="h-[3.25rem] border-b border-[#edebe9] px-4 flex items-center justify-between shrink-0"
      >
        <div class="flex items-center space-x-2.5 min-w-0">
          <RouterLink
            :to="{ name: 'communities' }"
            class="hidden max-md:block text-xl text-[#616161]"
            :aria-label="t('report.communities')"
          >
            ←
          </RouterLink>
          <div
            class="w-7 h-7 rounded-full shrink-0 flex items-center justify-center text-white text-[0.6875rem] font-semibold"
            :style="{ background: colorOf(community.name) }"
            aria-hidden="true"
          >
            {{ tileLabelOf(community.name) }}
          </div>
          <h2 class="font-bold text-base text-[#242424] flex items-center min-w-0">
            <span class="truncate">{{ community.name }}</span>
            <button
              v-if="isOwner"
              type="button"
              class="ml-1.5 p-1 text-[#616161] hover:text-black rounded"
              :title="t('common.edit')"
              :aria-label="t('common.edit')"
              @click="editingCommunity = true"
            >
              <svg
                class="w-3.5 h-3.5"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                viewBox="0 0 24 24"
              ><path
                d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
                stroke-linecap="round"
                stroke-linejoin="round"
              /></svg>
            </button>
          </h2>
        </div>

        <!-- Chat Top Actions (số thành viên, menu thêm) -->
        <div class="flex items-center space-x-3 text-[#616161]">
          <button
            type="button"
            class="flex items-center gap-1 rounded px-1.5 py-1 text-xs font-semibold text-[#4f52b2] hover:bg-[#4f52b2]/10"
            :title="t('ai.summarizeShort')"
            data-testid="ai-summary-button"
            @click="aiSummary = true"
          >
            <span aria-hidden="true">✦</span>{{ t('ai.summarizeShort') }}
          </button>
          <component
            :is="isOwner ? 'button' : 'div'"
            class="flex items-center space-x-1 rounded p-1"
            :class="{ 'hover:bg-black/5 cursor-pointer': isOwner }"
            :type="isOwner ? 'button' : undefined"
            :title="t('report.manageMembers')"
            data-testid="manage-members"
            @click="isOwner && (managingMembers = true)"
          >
            <svg
              class="w-4 h-4"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            ><path
              d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
              stroke-linecap="round"
              stroke-linejoin="round"
            /></svg>
            <span class="text-xs font-semibold text-[#242424]">{{ community.memberCount }}</span>
          </component>
          <MenuButton
            v-if="isOwner"
            :items="menuItems"
            :label="t('report.moreActions')"
            @select="onMenu"
          />
        </div>
      </div>

      <!-- Top Banner (thay "Invite others to chat"): mô tả cộng đồng + nút xem người chưa nộp -->
      <div
        v-if="community && (community.description || isOwner)"
        class="bg-[#f5f5f5] px-4 py-2 flex items-center justify-between border-b border-[#e0e0e0] text-xs"
      >
        <div class="flex items-center text-[#242424] font-normal min-w-0">
          <svg
            class="w-3.5 h-3.5 mr-2 text-[#616161] shrink-0"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
          ><path
            d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"
            stroke-linecap="round"
            stroke-linejoin="round"
          /></svg>
          <span class="truncate">{{ community.description || t('report.owner', { name: community.owner?.fullName ?? '' }) }}</span>
        </div>

        <div
          v-if="isOwner && pending"
          class="relative flex items-center space-x-2 shrink-0"
        >
          <button
            type="button"
            class="px-3 py-1 bg-white border border-[#d1d1d1] rounded text-[#242424] font-medium hover:bg-[#f0f0f0]"
            :aria-expanded="showPending"
            data-testid="pending"
            @click="showPending = !showPending"
          >
            {{ t('report.pendingToday') }} ({{ pending.missing.length }})
          </button>
          <div
            v-if="showPending"
            class="absolute top-9 right-0 z-30 min-w-[13.75rem] p-3 bg-white rounded-md shadow-xl ring-1 ring-black/10"
            role="dialog"
            :aria-label="t('report.pendingToday')"
          >
            <p
              v-if="!pending.workingDay"
              class="text-xs text-[#616161]"
              data-testid="pending-nonworking"
            >
              {{ t('report.notWorkingDay') }}
            </p>
            <p
              v-else-if="pending.missing.length === 0"
              class="text-xs text-[#616161]"
            >
              {{ t('report.everyoneSubmitted') }}
            </p>
            <ul
              v-else
              class="list-disc pl-4 text-[0.8125rem] leading-7"
            >
              <li
                v-for="p in pending.missing"
                :key="p.id"
              >
                {{ p.fullName }}<span
                  v-if="p.position"
                  class="text-xs text-[#616161]"
                > · {{ p.position }}</span>
              </li>
            </ul>
            <p
              v-if="pending.onLeave.length > 0"
              class="mt-2 border-t border-[#edebe9] pt-2 text-xs text-[#616161]"
              data-testid="pending-leave"
            >
              {{ t('report.onLeave') }}: {{ pending.onLeave.map((p) => p.fullName).join(', ') }}
            </p>
          </div>
        </div>
      </div>

      <!-- Tổng quan cộng đồng: mỗi thành viên một thẻ, bấm vào để xem báo cáo riêng của người đó -->
      <div
        v-if="userId === undefined"
        class="flex-1 overflow-y-auto px-6 py-6 max-md:px-3"
        data-testid="members-overview"
      >
        <AlertBanner v-if="errorMessage">
          {{ errorMessage }}
        </AlertBanner>
        <p class="mb-3 text-xs text-[#616161]">
          {{ t('report.pickMember') }}
        </p>
        <ul
          v-if="community"
          class="grid grid-cols-[repeat(auto-fill,minmax(14rem,1fr))] gap-3"
        >
          <li
            v-for="m in detail?.members ?? []"
            :key="m.id"
          >
            <RouterLink
              :to="{ name: 'member-reports', params: { id: id, userId: m.id } }"
              class="flex items-center gap-3 rounded-lg border border-[#e1dfdd] bg-white px-3 py-2.5 hover:border-[#4f52b2] hover:shadow-sm"
              data-testid="member-card"
            >
              <UserAvatar
                :name="m.fullName"
                :size="36"
              />
              <span class="min-w-0">
                <span class="block truncate text-[0.8125rem] font-semibold text-[#242424]">
                  {{ m.fullName }}<span
                    v-if="m.id === myId"
                    class="ml-1.5 rounded bg-[#4f52b2]/10 px-1.5 py-px text-[0.625rem] font-semibold text-[#4f52b2]"
                  >{{ t('report.you') }}</span>
                </span>
                <span class="block truncate text-[0.6875rem] text-[#616161]">{{ m.position ?? '' }}</span>
              </span>
            </RouterLink>
          </li>
        </ul>
        <p
          v-if="community && (detail?.members.length ?? 0) === 0"
          class="text-xs text-[#616161]"
        >
          {{ t('report.noMembersYet') }}
        </p>
      </div>

      <!-- Trang báo cáo riêng của một thành viên -->
      <div
        v-else
        class="flex min-h-0 flex-1 flex-col"
        data-testid="member-reports"
      >
        <div
          v-if="viewed"
          class="flex items-center gap-2.5 border-b border-[#edebe9] px-6 py-2 max-md:px-3"
        >
          <UserAvatar
            :name="viewed.fullName"
            :size="28"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate text-[0.8125rem] font-semibold">
              {{ t('report.reportsOf', { name: viewed.fullName }) }}<span
                v-if="isMine"
                class="ml-1.5 rounded bg-[#4f52b2]/10 px-1.5 py-px text-[0.625rem] font-semibold text-[#4f52b2]"
              >{{ t('report.you') }}</span>
            </p>
            <p
              v-if="!isMine"
              class="text-[0.6875rem] text-[#616161]"
            >
              {{ t('report.viewOnly') }}
            </p>
          </div>
          <button
            v-if="isMine && isMember"
            type="button"
            class="btn shrink-0"
            data-testid="dayoff-open"
            @click="dayOffOpen = true"
          >
            {{ t('report.dayOff.open') }}
          </button>
        </div>

        <!-- Chat Stream Messages Container -->
        <div
          ref="scroller"
          class="flex-1 overflow-y-auto px-6 py-4 space-y-4 max-md:px-3"
          data-purpose="chat-messages"
        >
          <AlertBanner v-if="errorMessage">
            {{ errorMessage }}
          </AlertBanner>
          <p
            v-if="loading"
            class="text-xs text-center text-[#616161]"
          >
            {{ t('common.loading') }}
          </p>

          <template v-if="community">
            <div
              v-if="hasMore"
              class="flex justify-center"
            >
              <button
                type="button"
                class="px-3.5 py-1 bg-white border border-[#e1dfdd] rounded-full text-xs font-semibold text-[#616161] hover:bg-[#f5f5f5] disabled:opacity-60"
                :disabled="loadingMore"
                @click="older"
              >
                {{ loadingMore ? t('common.loading') : t('report.loadOlder') }}
              </button>
            </div>

            <p
              v-if="!loading && posts.length === 0"
              class="text-xs text-center text-[#616161]"
              data-testid="no-posts"
            >
              {{ isMine ? t('report.noPosts') : t('report.noPostsOf', { name: viewed?.fullName ?? '' }) }}
              <button
                v-if="isMember && isMine"
                type="button"
                class="text-[#4f52b2] hover:underline"
                @click="openComposer"
              >
                {{ t('report.writeFirst') }}
              </button>
            </p>

            <template
              v-for="row in timeline"
              :key="row.key"
            >
              <!-- Timestamp Divider -->
              <div
                v-if="row.kind === 'day'"
                class="flex justify-center my-6"
              >
                <span class="text-[0.6875rem] font-normal text-[#616161]">{{ row.label }}</span>
              </div>
              <PostCard
                v-else
                :post="row.post"
                :is-mine="row.post.author?.id === myId"
                :can-moderate="isOwner"
                @updated="replacePost"
                @edit="openComposer"
              />
            </template>
          </template>
        </div>
      </div>

      <!-- Thanh "Viết báo cáo" ở đáy; bấm vào thì mở form phủ kín -->
      <div
        v-if="community && isMember && isMine"
        class="p-4 pt-1 bg-white"
        data-purpose="chat-input-container"
      >
        <div class="border border-[#e1dfdd] rounded-lg bg-white shadow-sm focus-within:border-[#5c5fc8] flex items-center justify-between px-3 py-2">
          <button
            type="button"
            class="w-full text-left text-xs text-[#707070] bg-transparent border-none p-0 cursor-text"
            data-testid="open-composer"
            @click="openComposer"
          >
            {{ myToday ? t('report.editToday') : t('report.writeToday') }}
          </button>
          <div class="flex items-center space-x-2 text-[#616161] shrink-0 ml-3">
            <button
              type="button"
              class="p-1 hover:text-black hover:bg-black/5 rounded"
              :aria-label="myToday ? t('report.editToday') : t('report.writeToday')"
              @click="openComposer"
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
      </div>
    </template>

    <MembersDialog
      v-if="managingMembers && detail"
      :detail="detail"
      @close="managingMembers = false"
      @changed="onCommunityChanged"
    />
    <DayOffDialog
      v-if="dayOffOpen"
      @close="dayOffOpen = false"
    />

    <AiSummaryDialog
      v-if="aiSummary && community"
      :title="`${t('ai.summarizeShort')}: ${community.name}`"
      :call="() => aiApi.summarizeCommunity(id, 7, aiLang)"
      @close="aiSummary = false"
    />

    <CommunityFormDialog
      v-if="editingCommunity && community"
      :community="community"
      @close="editingCommunity = false"
      @saved="onEdited"
    />
    <ModalDialog
      v-if="confirmDelete"
      :title="t('report.deleteCommunity')"
      @close="confirmDelete = false"
    >
      <p class="mb-4 text-sm">
        {{ t('report.deleteCommunityConfirm') }}
      </p>
      <div class="flex justify-end space-x-2">
        <button
          type="button"
          class="h-8 px-3.5 bg-white border border-[#e1dfdd] rounded-md text-[0.8125rem] font-semibold hover:bg-[#f5f5f5]"
          @click="confirmDelete = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="h-8 px-3.5 bg-white border border-[#e1dfdd] rounded-md text-[0.8125rem] font-semibold text-[#c4314b] hover:bg-[#f5f5f5]"
          @click="removeCommunity"
        >
          {{ t('common.confirm') }}
        </button>
      </div>
    </ModalDialog>
  </main>
  <!-- END: MainChatView -->
</template>

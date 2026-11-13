import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { toApiError } from '../../shared/api/http'
import * as reportApi from './api'
import type { CommunitySummary, Person } from './types'

/**
 * Danh sách cộng đồng ở cột bên trái của app Báo cáo. Trang feed bên phải cập nhật store này khi tạo, đổi tên,
 * thêm/bớt thành viên hay xoá cộng đồng, để cột trái luôn khớp mà không cần tải lại.
 */
export const useCommunityStore = defineStore('community', () => {
  const items = ref<CommunitySummary[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const errorCode = ref('')
  /** Thành viên của từng cộng đồng (id cộng đồng -> danh sách), dùng cho các nhãn tên dưới mỗi cộng đồng ở sidebar. */
  const members = ref<Record<number, Person[]>>({})

  const sorted = computed(() => [...items.value].sort((a, b) => a.name.localeCompare(b.name)))

  async function load() {
    loading.value = true
    errorCode.value = ''
    try {
      items.value = await reportApi.listCommunities()
      loaded.value = true
    } catch (error) {
      errorCode.value = toApiError(error).code
    } finally {
      loading.value = false
    }
  }

  function upsert(community: CommunitySummary) {
    const exists = items.value.some((c) => c.id === community.id)
    items.value = exists ? items.value.map((c) => (c.id === community.id ? community : c)) : [...items.value, community]
  }

  function remove(id: number) {
    items.value = items.value.filter((c) => c.id !== id)
    const rest = { ...members.value }
    delete rest[id]
    members.value = rest
  }

  function setMembers(communityId: number, list: Person[]) {
    members.value = { ...members.value, [communityId]: list }
  }

  /** Nạp thành viên của một cộng đồng nếu chưa có (mở nhãn tên ở sidebar). */
  async function loadMembers(communityId: number) {
    if (members.value[communityId]) return
    try {
      const detail = await reportApi.getCommunity(communityId)
      upsert(detail.community)
      setMembers(communityId, detail.members)
    } catch (error) {
      errorCode.value = toApiError(error).code
    }
  }

  function reset() {
    items.value = []
    members.value = {}
    loaded.value = false
  }

  return { items, sorted, members, loading, loaded, errorCode, load, upsert, remove, setMembers, loadMembers, reset }
})

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { playChime } from '../../shared/utils/sound'
import { useAuthStore } from '../auth/store'
import { useNotificationStore } from '../notification/store'
import * as chatApi from './api'
import { createChatSocket, type ChatSocket } from './socket'
import type { ChatEvent, ChatMessage, Conversation, SendInput } from './types'

/** Số tin tải mỗi lần (trang đầu và mỗi lần cuộn lên xem thêm). */
export const PAGE_SIZE = 30

interface MessageCache {
  /** Cũ trước, mới sau (đảo lại từ thứ tự server trả về) */
  items: ChatMessage[]
  /** Còn tin cũ hơn trên server */
  hasMore: boolean
  loading: boolean
  loaded: boolean
}

/** Access token còn dưới ngần này giây thì làm mới trước khi nối WebSocket. */
const TOKEN_MIN_LIFETIME_SECONDS = 60

function secondsLeft(token: string | null): number {
  if (!token) return 0
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number }
    return (payload.exp ?? 0) - Date.now() / 1000
  } catch {
    return 0
  }
}

/**
 * Trò chuyện thời gian thực. Giữ danh sách cuộc nhắn riêng, số chưa đọc, và bộ nhớ đệm tin nhắn của từng cuộc trò
 * chuyện đã mở. Server đẩy sự kiện qua WebSocket; mọi thao tác ghi đi qua REST rồi tự cập nhật theo kết quả (tin do
 * chính mình gửi cũng quay lại qua WebSocket nên được loại trùng theo id).
 */
export const useChatStore = defineStore('chat', () => {
  const auth = useAuthStore()
  const notifications = useNotificationStore()

  const conversations = ref<Conversation[]>([])
  const conversationsLoaded = ref(false)
  const messages = ref<Record<number, MessageCache>>({})
  /** Số chưa đọc theo id cuộc trò chuyện (cả nhắn riêng lẫn phòng nhóm). */
  const unreadByConversation = ref<Record<number, number>>({})
  /** Phòng chat của nhóm nào thì id cuộc trò chuyện là gì (để hiện chấm chưa đọc ở danh sách nhóm). */
  const teamOfConversation = ref<Record<number, number>>({})
  const connected = ref(false)
  /** Cuộc trò chuyện đang mở trên màn hình (đang xem thì tin mới đến được coi là đã đọc). */
  const activeId = ref<number | null>(null)

  let socket: ChatSocket | null = null
  let readTimer: ReturnType<typeof setTimeout> | undefined

  // ---- dữ liệu suy ra ----

  const totalUnread = computed(() => Object.values(unreadByConversation.value).reduce((sum, n) => sum + n, 0))

  /** Số chưa đọc của phòng chat từng nhóm (teamId -> số tin). */
  const unreadByTeam = computed(() => {
    const result: Record<number, number> = {}
    for (const [conversationId, count] of Object.entries(unreadByConversation.value)) {
      const teamId = teamOfConversation.value[Number(conversationId)]
      if (teamId !== undefined && count > 0) result[teamId] = count
    }
    return result
  })

  /** Lời mời nhắn tin người khác gửi cho mình, đang chờ mình trả lời. */
  const incomingRequests = computed(() =>
    conversations.value.filter((c) => c.status === 'PENDING' && !c.requestedByMe),
  )

  const myId = () => auth.user?.id ?? null

  function cacheOf(conversationId: number): MessageCache {
    if (!messages.value[conversationId]) {
      messages.value = { ...messages.value, [conversationId]: { items: [], hasMore: false, loading: false, loaded: false } }
    }
    // Trả về bản đã bọc reactive (không phải đối tượng thô vừa tạo) để gán loading/items còn kích hoạt cập nhật giao diện
    return messages.value[conversationId]
  }

  const EMPTY_CACHE: MessageCache = { items: [], hasMore: false, loading: false, loaded: false }

  /** Chỉ đọc (không tạo mới): dùng trong computed của giao diện để không ghi state khi đang tính. */
  function peek(conversationId: number): MessageCache {
    return messages.value[conversationId] ?? EMPTY_CACHE
  }

  function setUnread(conversationId: number, count: number) {
    unreadByConversation.value = { ...unreadByConversation.value, [conversationId]: Math.max(0, count) }
  }

  function sortConversations() {
    conversations.value = [...conversations.value].sort(
      (a, b) => Date.parse(b.last?.at ?? b.createdAt) - Date.parse(a.last?.at ?? a.createdAt) || b.id - a.id,
    )
  }

  function upsertConversation(conversation: Conversation) {
    if (conversation.type === 'TEAM') {
      if (conversation.team) {
        teamOfConversation.value = { ...teamOfConversation.value, [conversation.id]: conversation.team.id }
      }
      setUnread(conversation.id, conversation.unread)
      return
    }
    // Lời mời bị từ chối biến khỏi danh sách (khớp với danh sách server trả về)
    if (conversation.status === 'DECLINED') {
      removeConversation(conversation.id)
      return
    }
    const exists = conversations.value.some((c) => c.id === conversation.id)
    conversations.value = exists
      ? conversations.value.map((c) => (c.id === conversation.id ? conversation : c))
      : [...conversations.value, conversation]
    setUnread(conversation.id, conversation.unread)
    sortConversations()
  }

  function removeConversation(id: number) {
    conversations.value = conversations.value.filter((c) => c.id !== id)
    const unread = { ...unreadByConversation.value }
    delete unread[id]
    unreadByConversation.value = unread
    const cache = { ...messages.value }
    delete cache[id]
    messages.value = cache
  }

  // ---- tải dữ liệu ----

  async function loadConversations() {
    const list = await chatApi.listConversations()
    conversations.value = list
    conversationsLoaded.value = true
    const unread = { ...unreadByConversation.value }
    list.forEach((c) => (unread[c.id] = c.unread))
    unreadByConversation.value = unread
    sortConversations()
  }

  /** Số chưa đọc của mọi cuộc trò chuyện (gồm cả phòng nhóm). */
  async function loadUnread() {
    const summary = await chatApi.getUnread()
    const unread: Record<number, number> = {}
    const teams = { ...teamOfConversation.value }
    for (const entry of summary.conversations) {
      unread[entry.conversationId] = entry.count
      if (entry.teamId !== null) teams[entry.conversationId] = entry.teamId
    }
    unreadByConversation.value = unread
    teamOfConversation.value = teams
  }

  /** Tải lại những gì có thể đã lỡ trong lúc mất kết nối (và lúc mới vào). */
  async function sync() {
    try {
      await Promise.all([loadConversations(), loadUnread()])
      if (activeId.value !== null && messages.value[activeId.value]?.loaded) {
        await refreshLatest(activeId.value)
      }
    } catch {
      // Mất mạng: lần kết nối lại tới sẽ đồng bộ tiếp
    }
  }

  async function refreshLatest(conversationId: number) {
    const page = await chatApi.history(conversationId, null, PAGE_SIZE)
    const cache = cacheOf(conversationId)
    const latest = [...page.items].reverse()
    const known = new Set(latest.map((m) => m.id))
    // Giữ các tin cũ đã cuộn lên xem; thay phần mới nhất bằng bản vừa tải
    const oldest = latest[0]?.id ?? Number.MAX_SAFE_INTEGER
    cache.items = [...cache.items.filter((m) => m.id < oldest && !known.has(m.id)), ...latest]
    cache.hasMore = cache.hasMore || page.hasMore
  }

  // ---- mở một cuộc trò chuyện ----

  /** Mở cuộc trò chuyện: tải trang tin mới nhất (nếu chưa có) và đánh dấu đã đọc. */
  async function open(conversationId: number) {
    activeId.value = conversationId
    const cache = cacheOf(conversationId)
    if (!cache.loaded && !cache.loading) {
      cache.loading = true
      try {
        const page = await chatApi.history(conversationId, null, PAGE_SIZE)
        cache.items = [...page.items].reverse()
        cache.hasMore = page.hasMore
        cache.loaded = true
      } finally {
        cache.loading = false
      }
    }
    await markRead(conversationId)
  }

  function close(conversationId: number) {
    if (activeId.value === conversationId) activeId.value = null
  }

  /** Cuộn lên xem tin cũ hơn: tải thêm một trang và ghép vào đầu. */
  async function loadOlder(conversationId: number) {
    const cache = cacheOf(conversationId)
    if (cache.loading || !cache.hasMore || cache.items.length === 0) return
    cache.loading = true
    try {
      const page = await chatApi.history(conversationId, cache.items[0].id, PAGE_SIZE)
      const known = new Set(cache.items.map((m) => m.id))
      cache.items = [...[...page.items].reverse().filter((m) => !known.has(m.id)), ...cache.items]
      cache.hasMore = page.hasMore
    } finally {
      cache.loading = false
    }
  }

  async function markRead(conversationId: number) {
    if ((unreadByConversation.value[conversationId] ?? 0) === 0) return
    setUnread(conversationId, 0)
    try {
      await chatApi.markRead(conversationId)
    } catch {
      // Lần đồng bộ sau sẽ sửa lại số chưa đọc nếu lệnh này không tới được server
    }
  }

  /** Đánh dấu đã đọc sau một khoảng ngắn (gom nhiều tin đến liên tiếp thành một lệnh). */
  function markReadSoon(conversationId: number) {
    clearTimeout(readTimer)
    readTimer = setTimeout(() => void markRead(conversationId), 300)
  }

  // ---- gửi, sửa, xoá, biểu cảm ----

  function appendMessage(message: ChatMessage) {
    const cache = cacheOf(message.conversationId)
    if (!cache.loaded || cache.items.some((m) => m.id === message.id)) return
    cache.items = [...cache.items, message]
  }

  function replaceMessage(message: ChatMessage) {
    const cache = messages.value[message.conversationId]
    if (!cache) return
    cache.items = cache.items.map((m) => (m.id === message.id ? message : m))
  }

  /** Gửi một tin. Tin xuất hiện ngay từ kết quả trả về; bản đẩy qua WebSocket sau đó bị loại trùng. */
  async function send(conversationId: number, input: SendInput): Promise<ChatMessage> {
    const message = await chatApi.sendMessage(conversationId, input)
    appendMessage(message)
    touchLast(message)
    setUnread(conversationId, 0)
    return message
  }

  async function edit(messageId: number, body: string) {
    replaceMessage(await chatApi.editMessage(messageId, body))
  }

  async function remove(messageId: number) {
    await chatApi.deleteMessage(messageId)
    replaceMessage(await chatApi.getMessage(messageId))
  }

  async function toggleReaction(message: ChatMessage, emoji: string) {
    const mine = message.reactions.some((r) => r.emoji === emoji && r.reactedByMe)
    replaceMessage(
      mine ? await chatApi.removeMessageReaction(message.id, emoji) : await chatApi.reactToMessage(message.id, emoji),
    )
  }

  /** Cập nhật dòng "tin cuối" của cuộc trò chuyện trong danh sách khi có tin mới. */
  function touchLast(message: ChatMessage) {
    const found = conversations.value.find((c) => c.id === message.conversationId)
    if (!found) return
    conversations.value = conversations.value.map((c) =>
      c.id === message.conversationId
        ? {
            ...c,
            last: {
              id: message.id,
              authorId: message.author?.id ?? 0,
              authorName: message.author?.fullName ?? null,
              snippet: message.deleted ? null : (message.body ?? null),
              deleted: message.deleted,
              hasAttachment: message.attachments.length > 0,
              at: message.createdAt,
            },
          }
        : c,
    )
    sortConversations()
  }

  // ---- lời mời nhắn riêng ----

  async function startDirect(userId: number): Promise<Conversation> {
    const conversation = await chatApi.startDirect(userId)
    upsertConversation(conversation)
    return conversation
  }

  async function accept(id: number): Promise<Conversation> {
    const conversation = await chatApi.acceptRequest(id)
    upsertConversation(conversation)
    return conversation
  }

  async function decline(id: number) {
    await chatApi.declineRequest(id)
    removeConversation(id)
  }

  async function cancel(id: number) {
    await chatApi.cancelRequest(id)
    removeConversation(id)
  }

  /** Phòng chat của một nhóm; ghi nhớ để hiện chấm chưa đọc ở danh sách nhóm. */
  async function openTeamRoom(teamId: number): Promise<Conversation> {
    const conversation = await chatApi.teamConversation(teamId)
    upsertConversation(conversation)
    return conversation
  }

  // ---- sự kiện thời gian thực ----

  async function onEvent(event: ChatEvent) {
    try {
      switch (event.type) {
        case 'MESSAGE':
          await onMessage(event)
          break
        case 'MESSAGE_UPDATED':
          if (event.messageId !== null && messages.value[event.conversationId]?.loaded) {
            replaceMessage(await chatApi.getMessage(event.messageId))
          }
          // Tin cuối có thể vừa bị sửa hoặc xoá: tải lại dòng ở danh sách
          if (conversations.value.some((c) => c.id === event.conversationId)) await refreshConversation(event.conversationId)
          break
        case 'CONVERSATION':
          await refreshConversation(event.conversationId)
          break
        case 'CONVERSATION_REMOVED':
          removeConversation(event.conversationId)
          break
      }
    } catch {
      // Không tải lại được một sự kiện thì lần đồng bộ sau sẽ bù
    }
  }

  async function onMessage(event: ChatEvent) {
    const message = event.message
    if (!message) return
    appendMessage(message)
    touchLast(message)
    const mine = message.author?.id === myId()
    if (mine) {
      setUnread(message.conversationId, 0)
      return
    }
    const watching = activeId.value === message.conversationId && document.visibilityState === 'visible'
    if (watching) {
      markReadSoon(message.conversationId)
      return
    }
    // Cuộc trò chuyện chưa biết (ví dụ phòng nhóm chưa từng mở): tải thông tin để biết nó thuộc nhóm nào
    if (!conversations.value.some((c) => c.id === message.conversationId) && teamOfConversation.value[message.conversationId] === undefined) {
      await refreshConversation(message.conversationId)
    }
    setUnread(message.conversationId, (unreadByConversation.value[message.conversationId] ?? 0) + 1)
    if (notifications.soundOn) playChime()
  }

  async function refreshConversation(id: number) {
    try {
      upsertConversation(await chatApi.getConversation(id))
    } catch {
      // Không còn quyền xem hoặc đã bị xoá: bỏ khỏi danh sách
      removeConversation(id)
    }
  }

  // ---- vòng đời ----

  /** Mở kết nối thời gian thực và tải dữ liệu ban đầu. Gọi sau khi đăng nhập. */
  function start() {
    if (socket) return
    socket = createChatSocket({
      getFreshToken: async () => {
        if (secondsLeft(auth.accessToken) < TOKEN_MIN_LIFETIME_SECONDS) {
          try {
            await auth.refreshTokens()
          } catch {
            return null
          }
        }
        return auth.accessToken
      },
      onEvent: (event) => void onEvent(event),
      onConnect: () => {
        connected.value = true
        void sync()
      },
      onDisconnect: () => {
        connected.value = false
      },
    })
    socket.activate()
    void sync()
  }

  function stop() {
    socket?.deactivate()
    socket = null
    clearTimeout(readTimer)
    connected.value = false
  }

  function reset() {
    stop()
    conversations.value = []
    conversationsLoaded.value = false
    messages.value = {}
    unreadByConversation.value = {}
    teamOfConversation.value = {}
    activeId.value = null
  }

  return {
    conversations,
    conversationsLoaded,
    messages,
    unreadByConversation,
    unreadByTeam,
    totalUnread,
    incomingRequests,
    connected,
    activeId,
    cacheOf,
    peek,
    loadConversations,
    open,
    close,
    loadOlder,
    markRead,
    send,
    edit,
    remove,
    toggleReaction,
    startDirect,
    accept,
    decline,
    cancel,
    openTeamRoom,
    onEvent,
    sync,
    start,
    stop,
    reset,
  }
})

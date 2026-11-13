import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as authApi from '../auth/api'
import { useAuthStore } from '../auth/store'
import * as chatApi from './api'
import { useChatStore } from './store'
import type { ChatMessage, Conversation } from './types'

vi.mock('../auth/api')
vi.mock('./api')
vi.mock('./socket', () => ({ createChatSocket: vi.fn(() => ({ activate: vi.fn(), deactivate: vi.fn() })) }))
vi.mock('../notification/api', () => ({ unreadCount: vi.fn().mockResolvedValue(0) }))
vi.mock('../../shared/utils/sound', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/utils/sound')>()),
  playChime: vi.fn(),
}))

const person = (id: number, fullName = `User ${id}`) => ({ id, fullName, position: null })

const conversation = (overrides: Partial<Conversation> = {}): Conversation => ({
  id: 1,
  type: 'DIRECT',
  status: 'ACCEPTED',
  requestedByMe: false,
  canSend: true,
  other: person(2),
  team: null,
  last: null,
  unread: 0,
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

const message = (id: number, authorId = 2, overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  id,
  conversationId: 1,
  author: person(authorId),
  body: `m${id}`,
  deleted: false,
  replyTo: null,
  attachments: [],
  reactions: [],
  createdAt: `2026-01-01T00:00:0${id}Z`,
  editedAt: null,
  ...overrides,
})

async function loginAs(id: number) {
  vi.mocked(authApi.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: false,
  })
  vi.mocked(authApi.fetchMe).mockResolvedValue({
    id,
    email: 'x@soudan.local',
    fullName: 'Me',
    position: null,
    systemRole: 'USER',
    status: 'ACTIVE',
    mustChangePassword: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(authApi.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
  await useAuthStore().login('x@soudan.local', 'pw')
}

describe('chat store', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
  })

  it('should_loadNewestPageOldestFirstAndMarkRead_when_conversationOpened', async () => {
    await loginAs(1)
    const chat = useChatStore()
    chat.unreadByConversation = { 1: 2 }
    // Server trả mới nhất trước
    vi.mocked(chatApi.history).mockResolvedValue({ items: [message(3), message(2)], hasMore: true })

    await chat.open(1)

    expect(chat.cacheOf(1).items.map((m) => m.id)).toEqual([2, 3])
    expect(chat.cacheOf(1).hasMore).toBe(true)
    expect(chatApi.markRead).toHaveBeenCalledWith(1)
    expect(chat.unreadByConversation[1]).toBe(0)
  })

  it('should_prependOlderPageWithoutDuplicates_when_loadOlder', async () => {
    await loginAs(1)
    const chat = useChatStore()
    vi.mocked(chatApi.history).mockResolvedValueOnce({ items: [message(3), message(2)], hasMore: true })
    await chat.open(1)

    vi.mocked(chatApi.history).mockResolvedValueOnce({ items: [message(2), message(1)], hasMore: false })
    await chat.loadOlder(1)

    expect(chatApi.history).toHaveBeenLastCalledWith(1, 2, 30)
    expect(chat.cacheOf(1).items.map((m) => m.id)).toEqual([1, 2, 3])
    expect(chat.cacheOf(1).hasMore).toBe(false)
  })

  it('should_appendOnce_when_sameMessageArrivesFromSendAndSocket', async () => {
    await loginAs(1)
    const chat = useChatStore()
    vi.mocked(chatApi.history).mockResolvedValue({ items: [], hasMore: false })
    await chat.open(1)
    const mine = message(5, 1)
    vi.mocked(chatApi.sendMessage).mockResolvedValue(mine)

    await chat.send(1, { body: 'm5', files: [] })
    await chat.onEvent({ type: 'MESSAGE', conversationId: 1, messageId: 5, message: mine })

    expect(chat.cacheOf(1).items).toHaveLength(1)
  })

  it('should_countUnread_when_messageFromOtherArrivesOnClosedConversation', async () => {
    await loginAs(1)
    const chat = useChatStore()
    chat.conversations = [conversation()]

    await chat.onEvent({ type: 'MESSAGE', conversationId: 1, messageId: 7, message: message(7, 2) })

    expect(chat.unreadByConversation[1]).toBe(1)
    expect(chat.totalUnread).toBe(1)
  })

  it('should_notCountUnread_when_messageIsMine', async () => {
    await loginAs(1)
    const chat = useChatStore()
    chat.conversations = [conversation()]

    await chat.onEvent({ type: 'MESSAGE', conversationId: 1, messageId: 8, message: message(8, 1) })

    expect(chat.totalUnread).toBe(0)
  })

  it('should_markReadInsteadOfCounting_when_conversationIsOpenAndVisible', async () => {
    vi.useFakeTimers()
    try {
      await loginAs(1)
      const chat = useChatStore()
      chat.conversations = [conversation()]
      vi.mocked(chatApi.history).mockResolvedValue({ items: [], hasMore: false })
      await chat.open(1)

      await chat.onEvent({ type: 'MESSAGE', conversationId: 1, messageId: 9, message: message(9, 2) })

      expect(chat.unreadByConversation[1] ?? 0).toBe(0)
    } finally {
      vi.useRealTimers()
    }
  })

  it('should_removeConversation_when_requestDeclinedOrCancelled', async () => {
    await loginAs(1)
    const chat = useChatStore()
    chat.conversations = [conversation({ id: 4, status: 'PENDING' })]
    vi.mocked(chatApi.declineRequest).mockResolvedValue(conversation({ id: 4, status: 'DECLINED' }))

    await chat.decline(4)

    expect(chat.conversations).toEqual([])
  })

  it('should_listIncomingRequestsOnly_when_pendingAndNotRequestedByMe', async () => {
    await loginAs(1)
    const chat = useChatStore()
    chat.conversations = [
      conversation({ id: 1, status: 'PENDING', requestedByMe: true }),
      conversation({ id: 2, status: 'PENDING', requestedByMe: false }),
      conversation({ id: 3, status: 'ACCEPTED' }),
    ]

    expect(chat.incomingRequests.map((c) => c.id)).toEqual([2])
  })

  it('should_mapUnreadToTeam_when_teamRoomHasUnread', async () => {
    await loginAs(1)
    const chat = useChatStore()
    vi.mocked(chatApi.getUnread).mockResolvedValue({
      total: 3,
      conversations: [
        { conversationId: 10, teamId: 7, count: 3 },
        { conversationId: 11, teamId: null, count: 0 },
      ],
    })
    vi.mocked(chatApi.listConversations).mockResolvedValue([])

    await chat.sync()

    expect(chat.unreadByTeam).toEqual({ 7: 3 })
  })

  it('should_replaceMessage_when_reactionToggled', async () => {
    await loginAs(1)
    const chat = useChatStore()
    vi.mocked(chatApi.history).mockResolvedValue({ items: [message(2)], hasMore: false })
    await chat.open(1)
    const reacted = message(2, 2, { reactions: [{ emoji: '👍', count: 1, reactedByMe: true, users: ['Me'] }] })
    vi.mocked(chatApi.reactToMessage).mockResolvedValue(reacted)

    await chat.toggleReaction(chat.cacheOf(1).items[0], '👍')

    expect(chatApi.reactToMessage).toHaveBeenCalledWith(2, '👍')
    expect(chat.cacheOf(1).items[0].reactions[0].reactedByMe).toBe(true)
  })
})

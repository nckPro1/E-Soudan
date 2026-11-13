import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const chime = vi.fn()
const getUnreadCount = vi.fn()

vi.mock('../../shared/utils/sound', () => ({
  playChime: () => chime(),
  isSoundEnabled: () => true,
  setSoundEnabled: vi.fn(),
}))
vi.mock('./api', () => ({ getUnreadCount: () => getUnreadCount() }))

import { useNotificationStore } from './store'

describe('useNotificationStore sound', () => {
  beforeEach(() => {
    chime.mockClear()
    getUnreadCount.mockReset()
    setActivePinia(createPinia())
  })

  it('should_notChime_when_firstCountLoaded', async () => {
    getUnreadCount.mockResolvedValue(5)
    const store = useNotificationStore()
    await store.refresh()
    expect(store.unread).toBe(5)
    expect(chime).not.toHaveBeenCalled()
  })

  it('should_chime_when_unreadCountIncreases', async () => {
    getUnreadCount.mockResolvedValueOnce(2).mockResolvedValueOnce(3)
    const store = useNotificationStore()
    await store.refresh()
    await store.refresh()
    expect(store.unread).toBe(3)
    expect(chime).toHaveBeenCalledTimes(1)
  })

  it('should_notChime_when_unreadCountStaysOrDrops', async () => {
    getUnreadCount.mockResolvedValueOnce(4).mockResolvedValueOnce(4).mockResolvedValueOnce(1)
    const store = useNotificationStore()
    await store.refresh()
    await store.refresh()
    await store.refresh()
    expect(chime).not.toHaveBeenCalled()
  })

  it('should_notChime_when_countChangedByMarkingReadMyself', async () => {
    getUnreadCount.mockResolvedValueOnce(3).mockResolvedValueOnce(2)
    const store = useNotificationStore()
    await store.refresh()
    store.set(2)
    await store.refresh()
    expect(chime).not.toHaveBeenCalled()
  })

  it('should_notChime_when_soundIsMuted', async () => {
    getUnreadCount.mockResolvedValueOnce(0).mockResolvedValueOnce(1)
    const store = useNotificationStore()
    await store.refresh()
    store.toggleSound() // tắt: lần bật lại mới kêu thử
    expect(store.soundOn).toBe(false)
    await store.refresh()
    expect(chime).not.toHaveBeenCalled()
  })

  it('should_playPreview_when_soundTurnedBackOn', () => {
    const store = useNotificationStore()
    store.toggleSound() // tắt
    chime.mockClear()
    store.toggleSound() // bật lại
    expect(store.soundOn).toBe(true)
    expect(chime).toHaveBeenCalledTimes(1)
  })
})

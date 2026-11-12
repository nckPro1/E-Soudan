import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import en from '../../../shared/locales/en.json'
import { ApiError } from '../../../shared/types/api'
import * as authApi from '../../auth/api'
import { useAuthStore } from '../../auth/store'
import * as taskApi from '../api'
import type { Reaction, TaskComment } from '../types'
import TaskComments from './TaskComments.vue'

vi.mock('../../auth/api')
vi.mock('../api')
// Thư viện emoji là web component, không chạy trong jsdom: thay bằng bản giả chỉ phát sự kiện chọn emoji.
vi.mock('./EmojiPicker.vue', () => ({
  default: {
    emits: ['pick'],
    template: '<button data-testid="fake-picker" @click="$emit(\'pick\', \'🦄\')">pick</button>',
  },
}))

const ME = 10
const OTHER = 20

let seed: TaskComment[] = []

const comment = (id: number, authorId: number, body: string, extra: Partial<TaskComment> = {}): TaskComment => ({
  id,
  parentId: null,
  author: { id: authorId, fullName: authorId === ME ? 'Me' : 'Other' },
  body,
  deleted: false,
  createdAt: '2026-01-03T00:00:00Z',
  editedAt: null,
  reactions: [],
  ...extra,
})

const reaction = (emoji: string, count: number, reactedByMe: boolean): Reaction => ({
  emoji,
  count,
  reactedByMe,
  users: ['Me'],
})

async function mountComments(isLeader = false) {
  vi.mocked(authApi.login).mockResolvedValue({
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    expiresIn: 900,
    mustChangePassword: false,
  })
  vi.mocked(authApi.fetchMe).mockResolvedValue({
    id: ME,
    email: 'me@soudan.local',
    fullName: 'Me',
    position: null,
    systemRole: 'USER',
    status: 'ACTIVE',
    mustChangePassword: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(authApi.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
  vi.mocked(taskApi.listComments).mockResolvedValue(seed)
  await useAuthStore().login('me@soudan.local', 'pw')

  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const wrapper = mount(TaskComments, { props: { taskId: 3, isLeader }, global: { plugins: [i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

const buttonByText = (root: { findAll: (s: string) => { text: () => string; trigger: (e: string) => Promise<void> }[] }, text: string) =>
  root.findAll('button').find((b) => b.text() === text)!

describe('TaskComments', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    seed = []
  })

  it('should_listCommentsWithCount', async () => {
    seed = [comment(1, OTHER, 'Nhớ nộp file nhé')]
    const wrapper = await mountComments()

    expect(wrapper.text()).toContain('Comments (1)')
    expect(wrapper.text()).toContain('Nhớ nộp file nhé')
  })

  it('should_showEmptyMessage_whenNoComments', async () => {
    const wrapper = await mountComments()

    expect(wrapper.text()).toContain('No comments yet.')
  })

  it('should_showCommentAsPlainText_neverAsHtml', async () => {
    seed = [comment(1, OTHER, '<img src=x onerror=alert(1)>')]
    const wrapper = await mountComments()

    expect(wrapper.find('.body').text()).toBe('<img src=x onerror=alert(1)>')
    expect(wrapper.find('.body img').exists()).toBe(false)
  })

  it('should_postComment_andAppendItWithoutReload', async () => {
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(5, ME, 'Em xong rồi'))
    const wrapper = await mountComments()

    const area = wrapper.find('#new-comment')
    await area.setValue('  Em xong rồi  ')
    await wrapper.find('form.new-form').trigger('submit')
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledWith(3, 'Em xong rồi', undefined)
    expect(wrapper.text()).toContain('Em xong rồi')
    expect((area.element as HTMLTextAreaElement).value).toBe('')
  })

  it('should_disableSend_whenCommentBlank', async () => {
    const wrapper = await mountComments()
    const send = () => wrapper.find('form.new-form button[type="submit"]')

    expect(send().attributes('disabled')).toBeDefined()
    await wrapper.find('#new-comment').setValue('   ')
    expect(send().attributes('disabled')).toBeDefined()
    await wrapper.find('#new-comment').setValue('ok')
    expect(send().attributes('disabled')).toBeUndefined()
  })

  it('should_showErrorMessage_whenPostingFails', async () => {
    vi.mocked(taskApi.addComment).mockRejectedValue(new ApiError('FORBIDDEN', 403, 'x'))
    const wrapper = await mountComments()

    await wrapper.find('#new-comment').setValue('hi')
    await wrapper.find('form.new-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('You do not have permission to do this.')
  })

  // ---- trả lời ----

  it('should_nestRepliesUnderTheirParent', async () => {
    seed = [comment(1, OTHER, 'gốc'), comment(2, ME, 'trả lời', { parentId: 1 })]
    const wrapper = await mountComments()

    expect(wrapper.findAll('.threads > li')).toHaveLength(1)
    expect(wrapper.find('.replies').text()).toContain('trả lời')
  })

  it('should_replyToAComment_sendingItsId', async () => {
    seed = [comment(1, OTHER, 'gốc')]
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(2, ME, 'dạ vâng', { parentId: 1 }))
    const wrapper = await mountComments()

    await buttonByText(wrapper, 'Reply').trigger('click')
    expect(wrapper.text()).toContain('Replying to Other')
    await wrapper.find('[data-testid="reply-input"]').setValue('dạ vâng')
    await wrapper.find('form.reply-form').trigger('submit')
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledWith(3, 'dạ vâng', 1)
    expect(wrapper.find('.replies').text()).toContain('dạ vâng')
    expect(wrapper.find('form.reply-form').exists()).toBe(false)
  })

  it('should_replyToAReply_underTheSameThread', async () => {
    seed = [comment(1, OTHER, 'gốc'), comment(2, OTHER, 'trả lời 1', { parentId: 1 })]
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(3, ME, 'trả lời 2', { parentId: 1 }))
    const wrapper = await mountComments()

    await wrapper.find('.replies').findAll('button').find((b) => b.text() === 'Reply')!.trigger('click')
    await wrapper.find('[data-testid="reply-input"]').setValue('trả lời 2')
    await wrapper.find('form.reply-form').trigger('submit')
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledWith(3, 'trả lời 2', 2)
    expect(wrapper.findAll('.replies > li')).toHaveLength(2)
  })

  it('should_showPlaceholder_forDeletedParentThatKeepsReplies', async () => {
    seed = [comment(1, OTHER, '', { deleted: true, author: null, body: null }), comment(2, ME, 'còn đây', { parentId: 1 })]
    const wrapper = await mountComments()

    expect(wrapper.text()).toContain('This comment has been deleted.')
    expect(wrapper.text()).toContain('còn đây')
    expect(wrapper.text()).toContain('Comments (1)')
  })

  // ---- sửa ----

  it('should_offerEdit_onlyOnOwnComments', async () => {
    seed = [comment(1, ME, 'của tôi'), comment(2, OTHER, 'của người khác')]
    const wrapper = await mountComments(true) // kể cả Leader cũng không sửa lời người khác

    const edits = wrapper.findAll('button').filter((b) => b.text() === 'Edit')
    expect(edits).toHaveLength(1)
  })

  it('should_editComment_andShowEditedMark', async () => {
    seed = [comment(1, ME, 'bản đầu')]
    vi.mocked(taskApi.editComment).mockResolvedValue(comment(1, ME, 'bản sửa', { editedAt: '2026-01-04T00:00:00Z' }))
    const wrapper = await mountComments()

    await buttonByText(wrapper, 'Edit').trigger('click')
    const input = wrapper.find('[data-testid="edit-input"]')
    expect((input.element as HTMLTextAreaElement).value).toBe('bản đầu')
    await input.setValue('bản sửa')
    await wrapper.find('form.edit').trigger('submit')
    await flushPromises()

    expect(taskApi.editComment).toHaveBeenCalledWith(3, 1, 'bản sửa')
    expect(wrapper.text()).toContain('bản sửa')
    expect(wrapper.text()).toContain('edited')
    expect(wrapper.find('form.edit').exists()).toBe(false)
  })

  it('should_cancelEdit_withoutCallingServer', async () => {
    seed = [comment(1, ME, 'bản đầu')]
    const wrapper = await mountComments()

    await buttonByText(wrapper, 'Edit').trigger('click')
    await wrapper.find('form.edit').findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(taskApi.editComment).not.toHaveBeenCalled()
    expect(wrapper.find('form.edit').exists()).toBe(false)
  })

  // ---- xoá ----

  it('should_offerDelete_onlyForOwnCommentsOrToLeader', async () => {
    seed = [comment(1, ME, 'của tôi'), comment(2, OTHER, 'của người khác')]

    const member = await mountComments(false)
    expect(member.findAll('button.danger')).toHaveLength(1)
    member.unmount()

    const leader = await mountComments(true)
    expect(leader.findAll('button.danger')).toHaveLength(2)
  })

  it('should_deleteAfterConfirm_andReloadList', async () => {
    seed = [comment(1, ME, 'của tôi')]
    vi.mocked(taskApi.deleteComment).mockResolvedValue()
    const wrapper = await mountComments()

    vi.mocked(taskApi.listComments).mockResolvedValue([])
    await wrapper.find('button.danger').trigger('click')
    Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Confirm')?.click()
    await flushPromises()

    expect(taskApi.deleteComment).toHaveBeenCalledWith(3, 1)
    expect(taskApi.listComments).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.threads > li').exists()).toBe(false)
  })

  // ---- biểu cảm ----

  it('should_showReactionChips_withCountsAndMineHighlighted', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('👍', 2, true), reaction('🎉', 1, false)] })]
    const wrapper = await mountComments()

    const chips = wrapper.findAll('.chip').filter((c) => !c.classes().includes('add'))
    expect(chips).toHaveLength(2)
    expect(chips[0].text()).toContain('👍')
    expect(chips[0].text()).toContain('2')
    expect(chips[0].classes()).toContain('mine')
    expect(chips[1].classes()).not.toContain('mine')
    expect(chips[0].attributes('title')).toBe('Me')
  })

  it('should_addReaction_whenClickingChipIDidNotPress', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('🎉', 1, false)] })]
    vi.mocked(taskApi.reactToComment).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [reaction('🎉', 2, true)] }))
    const wrapper = await mountComments()

    await wrapper.find('.chip').trigger('click')
    await flushPromises()

    expect(taskApi.reactToComment).toHaveBeenCalledWith(3, 1, '🎉')
    expect(wrapper.find('.chip').classes()).toContain('mine')
    expect(wrapper.find('.chip').text()).toContain('2')
  })

  it('should_removeReaction_whenClickingMyChip', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('👍', 1, true)] })]
    vi.mocked(taskApi.removeReaction).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [] }))
    const wrapper = await mountComments()

    await wrapper.find('.chip').trigger('click')
    await flushPromises()

    expect(taskApi.removeReaction).toHaveBeenCalledWith(3, 1, '👍')
    expect(wrapper.findAll('.chip').filter((c) => !c.classes().includes('add'))).toHaveLength(0)
  })

  it('should_reactWithQuickEmoji_fromThePopover', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [reaction('❤️', 1, true)] }))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="add-reaction"]').trigger('click')
    await wrapper.find('.quick-btn[aria-label="❤️"]').trigger('click')
    await flushPromises()

    expect(taskApi.reactToComment).toHaveBeenCalledWith(3, 1, '❤️')
    expect(wrapper.find('.popover').exists()).toBe(false)
  })

  it('should_reactWithAnyEmoji_fromTheFullPicker', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [reaction('🦄', 1, true)] }))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="add-reaction"]').trigger('click')
    await wrapper.find('[data-testid="more-emoji"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="fake-picker"]').trigger('click')
    await flushPromises()

    expect(taskApi.reactToComment).toHaveBeenCalledWith(3, 1, '🦄')
  })

  it('should_showError_whenReactionRejected', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockRejectedValue(new ApiError('TOO_MANY_REACTIONS', 422, 'x'))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="add-reaction"]').trigger('click')
    await wrapper.find('.quick-btn').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('too many kinds of reactions')
  })
})

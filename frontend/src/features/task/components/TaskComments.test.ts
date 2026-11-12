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
  createdAt: new Date().toISOString(),
  editedAt: null,
  reactions: [],
  ...extra,
})

const reaction = (emoji: string, count: number, reactedByMe: boolean, users: string[] = ['Other']): Reaction => ({
  emoji,
  count,
  reactedByMe,
  users,
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

type W = Awaited<ReturnType<typeof mountComments>>
const actionNames = (root: W | ReturnType<W['find']>) =>
  (root as W).findAll('.meta button').map((b) => b.text())

describe('TaskComments', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    seed = []
  })

  // ---- hiển thị ----

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

  it('should_showAvatarWithInitialOfTheGivenName', async () => {
    seed = [comment(1, OTHER, 'x')]
    const wrapper = await mountComments()

    expect(wrapper.find('.thread .avatar').text()).toBe('O') // "Other" -> O
  })

  it('should_showCommentAsPlainText_neverAsHtml', async () => {
    seed = [comment(1, OTHER, '<img src=x onerror=alert(1)>')]
    const wrapper = await mountComments()

    expect(wrapper.find('.text').text()).toBe('<img src=x onerror=alert(1)>')
    expect(wrapper.find('.text img').exists()).toBe(false)
  })

  it('should_showRelativeTime_andFullDateInTooltip', async () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    seed = [comment(1, OTHER, 'x', { createdAt: fiveMinutesAgo })]
    const wrapper = await mountComments()

    const time = wrapper.find('time')
    expect(time.text()).toBe('5 minutes ago')
    expect(time.attributes('title')).toBeTruthy()
  })

  it('should_sayJustNow_forVeryRecentComment', async () => {
    seed = [comment(1, OTHER, 'x')]
    const wrapper = await mountComments()

    expect(wrapper.find('time').text()).toBe('Just now')
  })

  it('should_showEditedMark_whenCommentWasEdited', async () => {
    seed = [comment(1, OTHER, 'x', { editedAt: '2026-01-04T00:00:00Z' })]
    const wrapper = await mountComments()

    expect(wrapper.find('.meta').text()).toContain('Edited')
  })

  // ---- viết bình luận ----

  it('should_postComment_andAppendIt_whenPressingEnter', async () => {
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(5, ME, 'Em xong rồi'))
    const wrapper = await mountComments()

    const input = wrapper.find('.new-comment [data-testid="composer-input"]')
    await input.setValue('  Em xong rồi  ')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledWith(3, 'Em xong rồi', undefined)
    expect(wrapper.text()).toContain('Em xong rồi')
    expect((input.element as HTMLTextAreaElement).value).toBe('')
  })

  it('should_notPost_whenShiftEnterOrComposingWithIme', async () => {
    const wrapper = await mountComments()
    const input = wrapper.find('.new-comment [data-testid="composer-input"]')
    await input.setValue('dòng một')

    await input.trigger('keydown', { key: 'Enter', shiftKey: true })
    await input.trigger('keydown', { key: 'Enter', isComposing: true })

    expect(taskApi.addComment).not.toHaveBeenCalled()
  })

  it('should_postComment_whenClickingSendButton', async () => {
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(5, ME, 'ok'))
    const wrapper = await mountComments()

    await wrapper.find('.new-comment [data-testid="composer-input"]').setValue('ok')
    await wrapper.find('.new-comment [data-testid="composer-send"]').trigger('click')
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledOnce()
  })

  it('should_disableSend_andIgnoreEnter_whenCommentBlank', async () => {
    const wrapper = await mountComments()
    const send = wrapper.find('.new-comment [data-testid="composer-send"]')
    const input = wrapper.find('.new-comment [data-testid="composer-input"]')

    expect(send.attributes('disabled')).toBeDefined()
    await input.setValue('   ')
    await input.trigger('keydown', { key: 'Enter' })
    expect(send.attributes('disabled')).toBeDefined()
    expect(taskApi.addComment).not.toHaveBeenCalled()
    await input.setValue('ok')
    expect(send.attributes('disabled')).toBeUndefined()
  })

  it('should_showErrorMessage_whenPostingFails', async () => {
    vi.mocked(taskApi.addComment).mockRejectedValue(new ApiError('FORBIDDEN', 403, 'x'))
    const wrapper = await mountComments()

    const input = wrapper.find('.new-comment [data-testid="composer-input"]')
    await input.setValue('hi')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(wrapper.text()).toContain('You do not have permission to do this.')
  })

  // ---- phản hồi ----

  it('should_nestRepliesUnderTheirParent', async () => {
    seed = [comment(1, OTHER, 'gốc'), comment(2, ME, 'trả lời', { parentId: 1 })]
    const wrapper = await mountComments()

    expect(wrapper.findAll('.threads > .thread')).toHaveLength(1)
    expect(wrapper.find('.replies').text()).toContain('trả lời')
    expect(wrapper.find('.replies .comment.small').exists()).toBe(true)
  })

  it('should_replyToAComment_sendingItsId', async () => {
    seed = [comment(1, OTHER, 'gốc')]
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(2, ME, 'dạ vâng', { parentId: 1 }))
    const wrapper = await mountComments()

    await wrapper.findAll('.meta button').find((b) => b.text() === 'Reply')!.trigger('click')
    const input = wrapper.find('.reply-form [data-testid="composer-input"]')
    expect(input.attributes('placeholder')).toBe('Reply to Other...')
    await input.setValue('dạ vâng')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledWith(3, 'dạ vâng', 1)
    expect(wrapper.find('.replies').text()).toContain('dạ vâng')
    expect(wrapper.find('.reply-form').exists()).toBe(false)
  })

  it('should_closeReplyForm_whenPressingEscape', async () => {
    seed = [comment(1, OTHER, 'gốc')]
    const wrapper = await mountComments()

    await wrapper.findAll('.meta button').find((b) => b.text() === 'Reply')!.trigger('click')
    await wrapper.find('.reply-form [data-testid="composer-input"]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('.reply-form').exists()).toBe(false)
  })

  it('should_replyToAReply_underTheSameThread', async () => {
    seed = [comment(1, OTHER, 'gốc'), comment(2, OTHER, 'trả lời 1', { parentId: 1 })]
    vi.mocked(taskApi.addComment).mockResolvedValue(comment(3, ME, 'trả lời 2', { parentId: 1 }))
    const wrapper = await mountComments()

    await wrapper.find('.replies').findAll('.meta button').find((b) => b.text() === 'Reply')!.trigger('click')
    const input = wrapper.find('.reply-form [data-testid="composer-input"]')
    await input.setValue('trả lời 2')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(taskApi.addComment).toHaveBeenCalledWith(3, 'trả lời 2', 2)
    expect(wrapper.findAll('.replies .comment')).toHaveLength(2)
  })

  it('should_collapseLongThreads_andExpandOnDemand', async () => {
    seed = [
      comment(1, OTHER, 'gốc'),
      comment(2, OTHER, 'r1', { parentId: 1 }),
      comment(3, OTHER, 'r2', { parentId: 1 }),
      comment(4, OTHER, 'r3', { parentId: 1 }),
    ]
    const wrapper = await mountComments()

    expect(wrapper.findAll('.replies .comment')).toHaveLength(1)
    expect(wrapper.find('.replies').text()).toContain('r3')
    expect(wrapper.find('[data-testid="view-replies"]').text()).toBe('View 2 more replies')

    await wrapper.find('[data-testid="view-replies"]').trigger('click')

    expect(wrapper.findAll('.replies .comment')).toHaveLength(3)
    expect(wrapper.find('[data-testid="view-replies"]').exists()).toBe(false)
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

    expect(wrapper.findAll('.meta button').filter((b) => b.text() === 'Edit')).toHaveLength(1)
  })

  it('should_editComment_withEnter_andShowEditedMark', async () => {
    seed = [comment(1, ME, 'bản đầu')]
    vi.mocked(taskApi.editComment).mockResolvedValue(comment(1, ME, 'bản sửa', { editedAt: '2026-01-04T00:00:00Z' }))
    const wrapper = await mountComments()

    await wrapper.findAll('.meta button').find((b) => b.text() === 'Edit')!.trigger('click')
    const input = wrapper.find('[data-testid="edit-input"]')
    expect((input.element as HTMLTextAreaElement).value).toBe('bản đầu')
    await input.setValue('bản sửa')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(taskApi.editComment).toHaveBeenCalledWith(3, 1, 'bản sửa')
    expect(wrapper.text()).toContain('bản sửa')
    expect(wrapper.find('.meta').text()).toContain('Edited')
    expect(wrapper.find('form.edit').exists()).toBe(false)
  })

  it('should_cancelEdit_withEscape_withoutCallingServer', async () => {
    seed = [comment(1, ME, 'bản đầu')]
    const wrapper = await mountComments()

    await wrapper.findAll('.meta button').find((b) => b.text() === 'Edit')!.trigger('click')
    await wrapper.find('[data-testid="edit-input"]').trigger('keydown', { key: 'Escape' })

    expect(taskApi.editComment).not.toHaveBeenCalled()
    expect(wrapper.find('form.edit').exists()).toBe(false)
    expect(wrapper.text()).toContain('bản đầu')
  })

  // ---- xoá ----

  it('should_offerDelete_onlyForOwnCommentsOrToLeader', async () => {
    seed = [comment(1, ME, 'của tôi'), comment(2, OTHER, 'của người khác')]

    const member = await mountComments(false)
    expect(member.findAll('.meta button.danger')).toHaveLength(1)
    member.unmount()

    const leader = await mountComments(true)
    expect(leader.findAll('.meta button.danger')).toHaveLength(2)
  })

  it('should_deleteAfterConfirm_andReloadList', async () => {
    seed = [comment(1, ME, 'của tôi')]
    vi.mocked(taskApi.deleteComment).mockResolvedValue()
    const wrapper = await mountComments()

    vi.mocked(taskApi.listComments).mockResolvedValue([])
    await wrapper.find('.meta button.danger').trigger('click')
    Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Confirm')?.click()
    await flushPromises()

    expect(taskApi.deleteComment).toHaveBeenCalledWith(3, 1)
    expect(taskApi.listComments).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.threads > .thread').exists()).toBe(false)
  })

  // ---- biểu cảm ----

  it('should_notOfferLikeOrReact_onMyOwnComment', async () => {
    seed = [comment(1, ME, 'của tôi', { reactions: [reaction('👍', 1, false)] })]
    const wrapper = await mountComments()

    expect(wrapper.find('[data-testid="like"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="add-reaction"]').exists()).toBe(false)
    // Biểu cảm của người khác vẫn thấy được trên bình luận của mình
    expect(wrapper.find('[data-testid="reaction-summary"]').exists()).toBe(true)
  })

  it('should_offerLikeAndReact_onOthersComments', async () => {
    seed = [comment(1, OTHER, 'x')]
    const wrapper = await mountComments()

    expect(actionNames(wrapper)).toEqual(expect.arrayContaining(['Like', 'React', 'Reply']))
  })

  it('should_summarizeReactions_asOnePillWithTotal', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('🎉', 1, false), reaction('👍', 3, true, ['Me', 'A', 'B'])] })]
    const wrapper = await mountComments()

    const pill = wrapper.find('[data-testid="reaction-summary"]')
    expect(pill.text()).toContain('👍')
    expect(pill.text()).toContain('🎉')
    expect(pill.text()).toContain('4') // tổng 3 + 1
    expect(wrapper.findAll('[data-testid="reaction-summary"]')).toHaveLength(1)
  })

  it('should_listWhoReacted_whenClickingSummary', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('👍', 2, false, ['Lan', 'Minh'])] })]
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="reaction-summary"]').trigger('click')

    expect(wrapper.find('.summary .popover').text()).toContain('Lan, Minh')
  })

  it('should_toggleLike_withTheLikeLink', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [reaction('👍', 1, true, ['Me'])] }))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="like"]').trigger('click')
    await flushPromises()

    expect(taskApi.reactToComment).toHaveBeenCalledWith(3, 1, '👍')
    expect(wrapper.find('[data-testid="like"]').classes()).toContain('active')
  })

  it('should_removeLike_whenAlreadyLiked', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('👍', 1, true, ['Me'])] })]
    vi.mocked(taskApi.removeReaction).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [] }))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="like"]').trigger('click')
    await flushPromises()

    expect(taskApi.removeReaction).toHaveBeenCalledWith(3, 1, '👍')
    expect(wrapper.find('[data-testid="reaction-summary"]').exists()).toBe(false)
  })

  it('should_reactWithQuickEmoji_fromThePicker', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [reaction('❤️', 1, true, ['Me'])] }))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="add-reaction"]').trigger('click')
    await wrapper.find('.quick-btn[aria-label="❤️"]').trigger('click')
    await flushPromises()

    expect(taskApi.reactToComment).toHaveBeenCalledWith(3, 1, '❤️')
    expect(wrapper.find('.popover').exists()).toBe(false)
    // Trigger hiện emoji mình vừa thả
    expect(wrapper.find('[data-testid="add-reaction"]').text()).toContain('❤️')
  })

  it('should_removeMyReaction_whenPickingTheSameEmojiAgain', async () => {
    seed = [comment(1, OTHER, 'x', { reactions: [reaction('❤️', 1, true, ['Me'])] })]
    vi.mocked(taskApi.removeReaction).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [] }))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="add-reaction"]').trigger('click')
    expect(wrapper.find('.quick-btn[aria-label="❤️"]').classes()).toContain('selected')
    await wrapper.find('.quick-btn[aria-label="❤️"]').trigger('click')
    await flushPromises()

    expect(taskApi.removeReaction).toHaveBeenCalledWith(3, 1, '❤️')
  })

  it('should_reactWithAnyEmoji_fromTheFullPicker', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockResolvedValue(comment(1, OTHER, 'x', { reactions: [reaction('🦄', 1, true, ['Me'])] }))
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

    await wrapper.find('[data-testid="like"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('too many kinds of reactions')
  })

  it('should_showServerMessage_whenSelfReactionRejected', async () => {
    seed = [comment(1, OTHER, 'x')]
    vi.mocked(taskApi.reactToComment).mockRejectedValue(new ApiError('SELF_REACTION', 403, 'x'))
    const wrapper = await mountComments()

    await wrapper.find('[data-testid="like"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You cannot react to your own comment.')
  })
})

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { toApiError } from '../../../shared/api/http'
import { sanitizeForDisplay } from '../../../shared/utils/richtext'
import { useProfileStore } from '../../people/profile'
import ReactionPicker from '../../task/components/ReactionPicker.vue'
import * as reportApi from '../api'
import type { ReportPost } from '../types'
import PostAttachments from './PostAttachments.vue'
import PostComments from './PostComments.vue'

/**
 * Một báo cáo trong feed. Markup lấy nguyên từ "Message 3" của bản thiết kế Stitch (design/homepage-assets/teams.html):
 * ảnh đại diện bên trái, tên người gửi, bong bóng xám nhạt chứa nội dung (ba mục), viên reaction trắng ngay dưới
 * bong bóng. Thêm hàng "Bình luận" nhỏ và khu bình luận mở ra ngay dưới bài.
 */
const props = defineProps<{ post: ReportPost; isMine: boolean; canModerate: boolean }>()
const emit = defineEmits<{ updated: [post: ReportPost]; edit: [] }>()

const { t, te, locale } = useI18n()

const showComments = ref(false)
const commentCount = ref(props.post.commentCount)
const busy = ref(false)
const error = ref('')

const authorName = computed(() => props.post.author?.fullName ?? '')
const profile = useProfileStore()
const openAuthor = () => props.post.author && profile.open(props.post.author.id)
const sentAt = computed(() =>
  new Date(props.post.createdAt).toLocaleTimeString(locale.value, { hour: '2-digit', minute: '2-digit' }),
)

const html = computed(() => sanitizeForDisplay(props.post.body))

async function toggle(emoji: string) {
  if (busy.value || props.isMine) return
  busy.value = true
  error.value = ''
  try {
    const mine = props.post.reactions.some((r) => r.emoji === emoji && r.reactedByMe)
    emit(
      'updated',
      mine
        ? await reportApi.removePostReaction(props.post.id, emoji)
        : await reportApi.reactToPost(props.post.id, emoji),
    )
  } catch (err) {
    const code = toApiError(err).code
    error.value = te(`errors.${code}`) ? t(`errors.${code}`) : t('errors.UNKNOWN')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <!-- Message: người gửi + bong bóng + viên reaction (markup Stitch) -->
  <div
    class="flex items-start space-x-3 mt-4"
    data-testid="post"
  >
    <!-- User Avatar -->
    <div class="relative shrink-0 mt-1">
      <button
        type="button"
        class="rounded-full"
        :aria-label="authorName"
        @click="openAuthor"
      >
        <UserAvatar
          :name="authorName"
          :size="32"
        />
      </button>
    </div>
    <div class="flex-1 min-w-0">
      <div class="text-xs font-semibold text-[#616161] mb-1">
        <button
          type="button"
          class="font-semibold hover:underline"
          data-testid="post-author"
          @click="openAuthor"
        >
          {{ authorName }}
        </button>
        <span
          v-if="post.author?.position"
          class="font-normal"
        > · {{ post.author.position }}</span>
        <span class="font-normal text-[0.6875rem]"> · {{ sentAt }}<template v-if="post.edited"> · {{ t('report.edited') }}</template></span>
      </div>

      <!-- Tiêu đề + nội dung rich-text (đã lọc XSS ở server và lọc lại khi hiển thị) -->
      <div class="bg-[#f0f2f5] text-[#242424] px-4 py-3 rounded-2xl rounded-tl-sm text-[0.8125rem] leading-relaxed max-w-[40rem]">
        <h3
          class="post-title text-[0.9375rem] font-bold leading-snug"
          data-testid="post-title"
        >
          {{ post.title }}
        </h3>
        <!-- eslint-disable vue/no-v-html -->
        <div
          v-if="html"
          class="rich mt-1.5"
          data-testid="post-body"
          v-html="html"
        />
        <!-- eslint-enable vue/no-v-html -->
      </div>

      <PostAttachments
        v-if="post.attachments.length > 0"
        :post-id="post.id"
        :attachments="post.attachments"
      />

      <!-- Reaction Pill -->
      <div
        v-if="post.reactions.length > 0 || !isMine"
        class="flex items-center space-x-1.5 mt-1 bg-white border border-[#e1dfdd] rounded-full px-2 py-0.5 shadow-sm text-xs w-fit"
        data-testid="reactions"
      >
        <button
          v-for="r in post.reactions"
          :key="r.emoji"
          type="button"
          class="flex items-center hover:bg-gray-50 rounded-full disabled:cursor-default"
          :class="{ 'text-[#4f52b2] font-semibold': r.reactedByMe }"
          :disabled="busy || isMine"
          :title="r.users.join(', ')"
          :aria-pressed="r.reactedByMe"
          @click="toggle(r.emoji)"
        >
          <span class="text-sm mr-1">{{ r.emoji }}</span>{{ r.count }}
        </button>
        <ReactionPicker
          v-if="!isMine"
          plus
          :reactions="post.reactions"
          :disabled="busy"
          @pick="toggle"
        />
      </div>

      <p
        v-if="error"
        class="mt-1 text-xs text-[#c4314b]"
        role="alert"
      >
        {{ error }}
      </p>

      <div class="flex flex-wrap items-center space-x-1 mt-0.5">
        <button
          type="button"
          class="flex items-center space-x-1 px-2 py-1 rounded text-xs font-semibold text-[#616161] hover:bg-[#edebe9] hover:text-[#242424]"
          data-testid="toggle-comments"
          @click="showComments = !showComments"
        >
          <svg
            class="w-4 h-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path d="M4 5h16v11H9l-5 4z" /></svg>
          <span>{{ commentCount > 0 ? t('report.commentCount', { count: commentCount }) : t('report.comment') }}</span>
        </button>
        <button
          v-if="post.editable"
          type="button"
          class="px-2 py-1 rounded text-xs font-semibold text-[#616161] hover:bg-[#edebe9] hover:text-[#242424]"
          @click="emit('edit')"
        >
          {{ t('common.edit') }}
        </button>
      </div>

      <PostComments
        v-if="showComments"
        :post-id="post.id"
        :can-moderate="canModerate"
        @changed="commentCount = $event"
      />
    </div>
  </div>
</template>

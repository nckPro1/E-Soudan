<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { timeAgo } from '../../../shared/utils/time'
import type { AppNotification } from '../types'

/**
 * Một dòng trong mục Hoạt động, kiểu Teams: ảnh đại diện người gây ra thông báo kèm huy hiệu nhỏ theo loại, câu mô tả
 * (tên người và tên task/báo cáo in đậm, dựng theo ngôn ngữ đang chọn), đoạn trích, thời gian, và chấm xanh nếu chưa đọc.
 */
const props = defineProps<{ notification: AppNotification }>()
const emit = defineEmits<{ open: [] }>()

const { t, locale } = useI18n()

const actorName = computed(() => props.notification.actor?.fullName ?? t('notif.someone'))
const when = computed(() => timeAgo(props.notification.createdAt, locale.value) ?? t('task.justNow'))
const fullDate = computed(() => new Date(props.notification.createdAt).toLocaleString(locale.value))

const isReaction = computed(() => props.notification.type.endsWith('REACTION'))
/** Loại có đoạn trích đáng hiển thị (bình luận, lý do trả lại); biểu cảm thì emoji đã nằm trong câu. */
const showSnippet = computed(() => !isReaction.value && !!props.notification.snippet)

// Huy hiệu nhỏ ở góc ảnh đại diện: tick cho task, khung chat cho bình luận, tài liệu cho báo cáo; emoji cho biểu cảm
const BADGES = {
  task: 'M5 7l1.5 1.5L9 6M5 13l1.5 1.5L9 12M12 7h8M12 13h8',
  comment: 'M4 5h16v11H9l-5 4z',
  report: 'M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5',
}
const badge = computed(() => {
  const type = props.notification.type
  if (type.endsWith('REACTION')) return null
  if (type.startsWith('CHAT') || type.includes('COMMENT') || type.endsWith('REPLY')) return BADGES.comment
  return type.startsWith('TASK') ? BADGES.task : BADGES.report
})
</script>

<template>
  <button
    type="button"
    class="notification flex w-full items-start gap-3 border-b border-[#edebe9] px-4 py-3 text-left hover:bg-[#f5f5f5]"
    :class="notification.read ? 'read' : 'unread bg-[#4f52b2]/5'"
    :data-testid="notification.read ? 'notification-read' : 'notification-unread'"
    @click="emit('open')"
  >
    <span class="relative mt-0.5 shrink-0">
      <UserAvatar
        :name="actorName"
        :size="40"
      />
      <span
        class="absolute -bottom-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-[#4f52b2] text-[0.6875rem] leading-none text-white"
        aria-hidden="true"
      >
        <template v-if="isReaction">{{ notification.snippet }}</template>
        <svg
          v-else-if="badge"
          class="h-3 w-3"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        ><path :d="badge" /></svg>
      </span>
    </span>

    <span class="min-w-0 flex-1">
      <i18n-t
        :keypath="`notif.types.${notification.type}`"
        tag="span"
        class="block text-[0.8125rem] leading-snug text-[#242424]"
      >
        <template #actor>
          <strong class="font-semibold">{{ actorName }}</strong>
        </template>
        <template #subject>
          <strong class="font-semibold">{{ notification.subject ?? '' }}</strong>
        </template>
        <template #emoji>
          <span>{{ notification.snippet ?? '' }}</span>
        </template>
      </i18n-t>
      <span
        v-if="showSnippet"
        class="mt-0.5 block truncate text-xs italic text-[#616161]"
      >{{ notification.snippet }}</span>
      <time
        class="mt-0.5 block text-[0.6875rem] text-[#616161]"
        :datetime="notification.createdAt"
        :title="fullDate"
      >{{ when }}</time>
    </span>

    <span
      v-if="!notification.read"
      class="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-[#4f52b2]"
      :aria-label="t('notif.unread')"
      role="img"
    />
  </button>
</template>

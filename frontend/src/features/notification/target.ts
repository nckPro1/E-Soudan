import type { RouteLocationRaw } from 'vue-router'
import type { AppNotification } from './types'

/** Đường dẫn khi bấm vào một thông báo (null: thông báo không dẫn tới đâu). Dùng chung cho Hoạt động và trang tổng quan. */
export function notificationTarget(n: AppNotification): RouteLocationRaw | null {
  if (n.type === 'REPORT_REMINDER' && n.communityId !== null) {
    return { name: 'community-feed', params: { id: n.communityId } }
  }
  if (n.type.startsWith('TASK') && n.taskId !== null) {
    return { name: 'task-detail', params: { id: n.taskId } }
  }
  if (n.type.startsWith('REPORT') && n.communityId !== null && n.postAuthorId !== null) {
    return { name: 'member-reports', params: { id: n.communityId, userId: n.postAuthorId } }
  }
  if (n.type.startsWith('CHAT') && n.conversationId !== null) {
    return { name: 'chat-conversation', params: { id: n.conversationId } }
  }
  return null
}

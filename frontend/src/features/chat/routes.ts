import type { RouteRecordRaw } from 'vue-router'

export const chatRoutes: RouteRecordRaw[] = [
  {
    // Hai cột như Teams: danh sách cuộc nhắn riêng bên trái, cuộc trò chuyện đang chọn bên phải
    path: '/chat',
    component: () => import('./views/ChatLayout.vue'),
    meta: { fullBleed: true },
    children: [
      {
        path: '',
        name: 'chat',
        component: () => import('./views/ChatEmptyView.vue'),
      },
      {
        path: ':id(\\d+)',
        name: 'chat-conversation',
        component: () => import('./views/ConversationView.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
      },
    ],
  },
]

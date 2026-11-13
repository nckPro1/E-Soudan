import type { RouteRecordRaw } from 'vue-router'

export const aiRoutes: RouteRecordRaw[] = [
  {
    // Trợ lý AI: hỏi đáp có nguồn, tóm tắt báo cáo, kế hoạch, viết hộ
    path: '/ai',
    name: 'ai',
    component: () => import('./views/AiView.vue'),
    meta: { fullBleed: true },
  },
]

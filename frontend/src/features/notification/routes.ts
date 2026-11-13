import type { RouteRecordRaw } from 'vue-router'

export const notificationRoutes: RouteRecordRaw[] = [
  {
    // Mục Hoạt động: thông báo trong ứng dụng
    path: '/activity',
    name: 'activity',
    component: () => import('./views/ActivityView.vue'),
  },
]

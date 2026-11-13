import { createRouter, createWebHistory, type RouteRecordRaw, type RouterHistory } from 'vue-router'
import { installGuards } from './guards'
import { authRoutes } from '../features/auth/routes'
import { userRoutes } from '../features/user/routes'
import { teamRoutes } from '../features/team/routes'
import { taskRoutes } from '../features/task/routes'
import { reportRoutes } from '../features/report/routes'
import { chatRoutes } from '../features/chat/routes'
import { notificationRoutes } from '../features/notification/routes'
import { calendarRoutes } from '../features/calendar/routes'
import { aiRoutes } from '../features/ai/routes'

const routes: RouteRecordRaw[] = [
  ...authRoutes,
  {
    // Mọi trang sau đăng nhập nằm trong khung bố cục chung
    path: '/',
    component: () => import('../shared/components/AppLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('./HomeView.vue') },
      ...userRoutes,
      ...teamRoutes,
      ...taskRoutes,
      ...reportRoutes,
      ...chatRoutes,
      ...notificationRoutes,
      ...calendarRoutes,
      ...aiRoutes,
    ],
  },
]

/** Tách thành hàm để test dùng history trong bộ nhớ. */
export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({ history, routes })
  installGuards(router)
  return router
}

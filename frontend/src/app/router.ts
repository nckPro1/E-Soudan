import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { authRoutes } from '../features/auth/routes'
import { userRoutes } from '../features/user/routes'
import { teamRoutes } from '../features/team/routes'
import { taskRoutes } from '../features/task/routes'
import { reportRoutes } from '../features/report/routes'
import { chatRoutes } from '../features/chat/routes'
import { notificationRoutes } from '../features/notification/routes'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('./HomeView.vue') },
  ...authRoutes,
  ...userRoutes,
  ...teamRoutes,
  ...taskRoutes,
  ...reportRoutes,
  ...chatRoutes,
  ...notificationRoutes,
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

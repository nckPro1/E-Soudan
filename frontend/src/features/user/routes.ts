import type { RouteRecordRaw } from 'vue-router'

export const userRoutes: RouteRecordRaw[] = [
  {
    path: '/users',
    name: 'users',
    component: () => import('./views/UserListView.vue'),
    meta: { permission: 'USER_READ' },
  },
]

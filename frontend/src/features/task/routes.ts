import type { RouteRecordRaw } from 'vue-router'

export const taskRoutes: RouteRecordRaw[] = [
  {
    path: '/tasks/mine',
    name: 'my-tasks',
    component: () => import('./views/MyTasksView.vue'),
  },
  {
    path: '/tasks/:id(\\d+)',
    name: 'task-detail',
    component: () => import('./views/TaskDetailView.vue'),
    props: (route) => ({ id: Number(route.params.id) }),
  },
]

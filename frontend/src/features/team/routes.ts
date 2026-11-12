import type { RouteRecordRaw } from 'vue-router'

export const teamRoutes: RouteRecordRaw[] = [
  {
    path: '/teams',
    name: 'teams',
    component: () => import('./views/TeamListView.vue'),
  },
  {
    path: '/teams/:id(\\d+)',
    name: 'team-detail',
    component: () => import('./views/TeamDetailView.vue'),
    props: (route) => ({ id: Number(route.params.id) }),
  },
]

import type { RouteRecordRaw } from 'vue-router'

export const teamRoutes: RouteRecordRaw[] = [
  {
    // Hai cột như Teams: danh sách nhóm bên trái, nhóm đang chọn (tab Trò chuyện/Thành viên/Công việc) bên phải
    path: '/teams',
    component: () => import('./views/TeamsLayout.vue'),
    meta: { fullBleed: true },
    children: [
      {
        path: '',
        name: 'teams',
        component: () => import('./views/TeamEmptyView.vue'),
      },
      {
        path: ':id(\\d+)',
        name: 'team-detail',
        component: () => import('./views/TeamDetailView.vue'),
        props: (route) => ({ id: Number(route.params.id), tab: route.query.tab }),
      },
    ],
  },
]

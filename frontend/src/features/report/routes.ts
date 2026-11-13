import type { RouteRecordRaw } from 'vue-router'

export const reportRoutes: RouteRecordRaw[] = [
  {
    // Bố cục hai cột như Teams: cột trái là danh sách cộng đồng, khung phải là feed của cộng đồng đang chọn
    path: '/communities',
    component: () => import('./views/CommunitiesLayout.vue'),
    meta: { fullBleed: true },
    children: [
      {
        path: '',
        name: 'communities',
        component: () => import('./views/CommunityEmptyView.vue'),
      },
      {
        // Tổng quan cộng đồng: danh sách thành viên (mỗi người một trang báo cáo riêng)
        path: ':id(\\d+)',
        name: 'community-feed',
        component: () => import('./views/CommunityFeedView.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
      },
      {
        // Trang báo cáo riêng của MỘT thành viên: ai cũng xem được, chỉ chính người đó đăng và sửa
        path: ':id(\\d+)/members/:userId(\\d+)',
        name: 'member-reports',
        component: () => import('./views/CommunityFeedView.vue'),
        props: (route) => ({ id: Number(route.params.id), userId: Number(route.params.userId) }),
      },
    ],
  },
]

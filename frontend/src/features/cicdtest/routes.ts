import type { RouteRecordRaw } from 'vue-router'

export const cicdTestRoutes: RouteRecordRaw[] = [
  {
    // Trang chỉ để thử luồng CI/CD, không có chức năng nghiệp vụ
    path: '/cicd-test',
    name: 'cicd-test',
    component: () => import('./views/CicdTestView.vue'),
  },
]

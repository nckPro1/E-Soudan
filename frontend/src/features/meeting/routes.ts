import type { RouteRecordRaw } from 'vue-router'

/** Phòng họp nằm ngoài khung ứng dụng và công khai: khách có link vào được mà không cần tài khoản. */
export const meetingRoutes: RouteRecordRaw[] = [
  {
    path: '/meet/:room',
    name: 'meeting',
    component: () => import('./views/MeetingRoomView.vue'),
    meta: { public: true },
  },
]

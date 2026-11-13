import type { RouteRecordRaw } from 'vue-router'

export const calendarRoutes: RouteRecordRaw[] = [
  {
    // Lịch cá nhân: ai cũng có, ghi chú đánh dấu theo ngày
    path: '/calendar',
    name: 'calendar',
    component: () => import('./views/CalendarView.vue'),
    meta: { fullBleed: true },
  },
]

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Chỉ dành cho người chưa đăng nhập (trang đăng nhập). Mặc định mọi route đều yêu cầu đăng nhập. */
    guestOnly?: boolean
    /** Quyền cần có (chuỗi từ GET /me/permissions) để vào route; thiếu thì về trang chủ. */
    permission?: string
  }
}

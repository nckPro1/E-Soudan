import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Chỉ dành cho người chưa đăng nhập (trang đăng nhập). Mặc định mọi route đều yêu cầu đăng nhập. */
    guestOnly?: boolean
    /** Quyền cần có (chuỗi từ GET /me/permissions) để vào route; thiếu thì về trang chủ. */
    permission?: string
    /** Trang tự dựng bố cục hai cột (danh sách + nội dung) nên khung nội dung không đệm lề và không tự cuộn. */
    fullBleed?: boolean
    /** App chưa làm: trang "Sắp có" lấy nội dung mô tả theo khoá này (soon.<module>). */
    module?: 'activity' | 'ai'
  }
}

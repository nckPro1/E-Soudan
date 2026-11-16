/** Cấu hình họp video công khai (không chứa bí mật): appId/domain chỉ để nhúng khung họp. */
export interface MeetingConfig {
  enabled: boolean
  appId: string | null
  domain: string | null
}

/** Phiên họp để mở khung Jitsi: jwt do backend ký (chủ trì có quyền duyệt, khách thì không). */
export interface MeetingSession {
  room: string
  appId: string
  domain: string
  jwt: string
}

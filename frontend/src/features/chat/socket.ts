import { Client } from '@stomp/stompjs'
import type { ChatEvent } from './types'

/** Hàng đợi riêng của mình: server đẩy sự kiện tới đây (xem WebSocketConfig ở backend). */
const QUEUE = '/user/queue/chat'

export interface ChatSocketOptions {
  /** Đảm bảo access token còn hạn rồi trả về (làm mới nếu cần). null nghĩa là chưa đăng nhập. */
  getFreshToken: () => Promise<string | null>
  onEvent: (event: ChatEvent) => void
  /** Vừa (kết nối lại) thành công: nơi tải bù những gì đã lỡ trong lúc mất kết nối. */
  onConnect: () => void
  onDisconnect: () => void
}

export interface ChatSocket {
  activate: () => void
  deactivate: () => void
}

function brokerUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

/**
 * Kết nối WebSocket (STOMP) tới máy chủ. Token gửi trong khung CONNECT (trình duyệt không gửi được header lúc bắt tay)
 * và được lấy mới mỗi lần kết nối, nên tự nối lại sau khi token hết hạn. Mất kết nối thì tự thử lại sau 5 giây; client
 * chỉ nghe, mọi thao tác ghi đi qua REST.
 */
export function createChatSocket(options: ChatSocketOptions): ChatSocket {
  const client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      const token = await options.getFreshToken()
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },
    onConnect: () => {
      client.subscribe(QUEUE, (message) => {
        try {
          options.onEvent(JSON.parse(message.body) as ChatEvent)
        } catch {
          // Gói tin hỏng: bỏ qua, lần tải lại sau sẽ đồng bộ
        }
      })
      options.onConnect()
    },
    onWebSocketClose: () => options.onDisconnect(),
  })
  return {
    activate: () => client.activate(),
    deactivate: () => {
      void client.deactivate()
    },
  }
}

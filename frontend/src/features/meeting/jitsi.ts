/** Phần tối thiểu của Jitsi IFrame API mà trang họp dùng. */
export interface JitsiApi {
  executeCommand: (command: string, ...args: unknown[]) => void
  addListener: (event: string, listener: (...args: unknown[]) => void) => void
  dispose: () => void
}

interface JitsiOptions {
  roomName: string
  jwt: string
  parentNode: HTMLElement
  width: string
  height: string
  lang: string
  configOverwrite?: Record<string, unknown>
}

type JitsiConstructor = new (domain: string, options: JitsiOptions) => JitsiApi

declare global {
  interface Window {
    JitsiMeetExternalAPI?: JitsiConstructor
  }
}

const loading = new Map<string, Promise<JitsiConstructor>>()

/** Nạp external_api.js của JaaS (một lần cho mỗi appId). Tên miền và appId lấy từ backend nên không tin chuỗi lạ. */
export function loadJitsi(domain: string, appId: string): Promise<JitsiConstructor> {
  if (!/^[a-z0-9.-]+$/i.test(domain) || !/^[\w-]+$/.test(appId)) {
    return Promise.reject(new Error('Cấu hình họp video không hợp lệ'))
  }
  const key = `${domain}/${appId}`
  const existing = loading.get(key)
  if (existing) return existing
  const promise = new Promise<JitsiConstructor>((resolve, reject) => {
    if (window.JitsiMeetExternalAPI) return resolve(window.JitsiMeetExternalAPI)
    const script = document.createElement('script')
    script.src = `https://${domain}/${appId}/external_api.js`
    script.async = true
    script.onload = () => (window.JitsiMeetExternalAPI ? resolve(window.JitsiMeetExternalAPI) : reject(new Error('Không nạp được Jitsi')))
    script.onerror = () => {
      loading.delete(key)
      reject(new Error('Không tải được thư viện họp video'))
    }
    document.head.appendChild(script)
  })
  loading.set(key, promise)
  return promise
}

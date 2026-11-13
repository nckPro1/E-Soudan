/**
 * Âm "ting" khi có thông báo mới. Tạo trực tiếp bằng Web Audio API (hai nốt sin ngắn, vui tai, không cần file âm
 * thanh). Trình duyệt chỉ cho phát âm sau khi người dùng đã tương tác với trang (bấm, gõ phím) nên nếu chưa thì lần
 * phát đó im lặng, không báo lỗi.
 */

const STORAGE_KEY = 'soudan.notificationSound'

type AudioContextCtor = typeof AudioContext

let context: AudioContext | null = null

function contextCtor(): AudioContextCtor | undefined {
  const w = window as unknown as { AudioContext?: AudioContextCtor; webkitAudioContext?: AudioContextCtor }
  return w.AudioContext ?? w.webkitAudioContext
}

/** Phát một tiếng chuông ngắn (khoảng nửa giây). */
export function playChime(): void {
  try {
    const Ctor = contextCtor()
    if (!Ctor) return
    context ??= new Ctor()
    const audio = context
    if (audio.state === 'suspended') void audio.resume()
    const now = audio.currentTime
    // Hai nốt đi lên: Sol5 rồi Mi6
    const notes: Array<[number, number]> = [
      [784, 0],
      [1318.5, 0.13],
    ]
    for (const [frequency, delay] of notes) {
      const osc = audio.createOscillator()
      const gain = audio.createGain()
      osc.type = 'sine'
      osc.frequency.value = frequency
      gain.gain.setValueAtTime(0.0001, now + delay)
      gain.gain.exponentialRampToValueAtTime(0.16, now + delay + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, now + delay + 0.35)
      osc.connect(gain)
      gain.connect(audio.destination)
      osc.start(now + delay)
      osc.stop(now + delay + 0.4)
    }
  } catch {
    // Không có âm thanh thì thôi: thông báo vẫn hiện bình thường
  }
}

/** Người dùng có bật âm thanh thông báo không. Mặc định là bật. */
export function isSoundEnabled(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off'
  } catch {
    return true
  }
}

export function setSoundEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off')
  } catch {
    // Chế độ riêng tư: không lưu được thì chỉ giữ trong phiên này
  }
}

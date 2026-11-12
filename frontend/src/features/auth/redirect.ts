/**
 * Chỉ chấp nhận đường dẫn nội bộ của ứng dụng làm đích sau đăng nhập.
 * Chặn "//evil.com", "https://evil.com", "/\evil.com" (chuyển hướng ra ngoài sau khi đăng nhập).
 */
export function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return '/'
  }
  return value
}

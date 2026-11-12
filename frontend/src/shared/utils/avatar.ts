// Màu lấy từ bảng màu của design system (chàm, son, matcha) và vài màu trầm hợp tông
const PALETTE = ['#274a78', '#c8402a', '#7a8f5a', '#8a6d3b', '#5b4b8a', '#2f7f86', '#a2566b']

/** Chữ cái đại diện: chữ đầu của từ cuối trong tên (tên Việt/Nhật thì đó là tên gọi). */
export function initialOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const last = words[words.length - 1] ?? ''
  return last ? Array.from(last)[0].toUpperCase() : '?'
}

/** Màu nền cố định theo tên: cùng một người luôn cùng một màu. */
export function colorOf(name: string): string {
  let hash = 0
  for (const ch of name) {
    hash = (hash * 31 + ch.codePointAt(0)!) | 0
  }
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

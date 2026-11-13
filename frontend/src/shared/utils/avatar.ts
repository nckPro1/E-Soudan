// Bảng màu ảnh đại diện theo tông Teams (tím chàm, đỏ, xanh lá, tím nhạt, xanh dương, ngọc, cam)
const PALETTE = ['#5b5fc7', '#c4314b', '#107c10', '#8764b8', '#0078d4', '#038387', '#ca5010']

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

/** Nhãn cho ô vuông đại diện (cộng đồng, nhóm): chữ đầu của tối đa hai từ đầu, ví dụ "Phòng phát triển số 2" -> "PP". */
export function tileLabelOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const letters = words.slice(0, 2).map((w) => Array.from(w)[0].toUpperCase())
  return letters.join('') || '?'
}

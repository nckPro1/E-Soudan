/**
 * Chèn một đoạn văn bản (ví dụ emoji) vào vị trí con trỏ của ô nhập, thay phần đang bôi đen nếu có.
 * Trả về nội dung mới và vị trí con trỏ sau khi chèn. Quá {@code max} ký tự thì giữ nguyên nội dung cũ.
 */
export function insertAtCaret(
  value: string,
  start: number | null,
  end: number | null,
  text: string,
  max: number,
): { value: string; caret: number } {
  const from = Math.min(start ?? value.length, value.length)
  const to = Math.min(end ?? from, value.length)
  const next = value.slice(0, from) + text + value.slice(Math.max(to, from))
  if (next.length > max) return { value, caret: from }
  return { value: next, caret: from + text.length }
}

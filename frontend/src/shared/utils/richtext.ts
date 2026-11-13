import DOMPurify from 'dompurify'

/**
 * Tiện ích cho nội dung rich-text (HTML) của trình soạn thảo Tiptap. Backend đã lọc HTML trước khi lưu; ở đây lọc
 * thêm một lớp nữa khi hiển thị (không tin dữ liệu từ bất kỳ nguồn nào), chỉ giữ các thẻ mà thanh công cụ tạo ra.
 */

const ALLOWED_TAGS = [
  'p', 'br', 'h1', 'h2', 'h3', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'hr', 'a',
]

// Link luôn mở tab mới và không gửi referrer
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer nofollow')
  }
})

/** HTML an toàn để gắn bằng v-html. */
export function sanitizeForDisplay(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR: ['href', 'rel', 'target'] })
}

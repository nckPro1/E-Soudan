/** Dung lượng file dễ đọc: "512 KB", "2.4 MB". */
export function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}

/** Phần mở rộng (chữ thường) của tên file; rỗng nếu không có. */
export function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/** Loại file backend chấp nhận (phải khớp UploadPolicy ở backend). */
export const ALLOWED_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'zip']
export const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp']
export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const MAX_FILES_PER_POST = 10

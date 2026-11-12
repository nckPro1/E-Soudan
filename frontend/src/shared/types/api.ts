/** Khung phản hồi của backend (ARCHITECTURE.md mục 5.2). */
export interface ApiSuccess<T> {
  success: true
  data: T
}

export interface FieldDetail {
  field: string
  message: string
}

export interface ApiErrorBody {
  success: false
  code: string
  message: string
  details?: FieldDetail[]
  timestamp: string
}

/** Lỗi đã chuẩn hoá từ mọi lỗi HTTP; giao diện chỉ làm việc với kiểu này. */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: FieldDetail[]

  constructor(code: string, status: number, message: string, details: FieldDetail[] = []) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

/** Một trang kết quả (page đếm từ 0), khớp PageResponse của backend. */
export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

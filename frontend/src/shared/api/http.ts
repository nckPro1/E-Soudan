import axios from 'axios'

// Instance axios dùng chung. Interceptor gắn token và xử lý 401/lỗi chung thêm ở Stage 1.13.
export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

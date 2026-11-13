import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * Thẻ hồ sơ đang mở (như bấm vào ảnh một người trong Teams). Bất kỳ chỗ nào hiện tên hoặc ảnh một người đều gọi
 * `open(userId)`; thẻ được dựng một lần ở khung ứng dụng (ProfileCardHost) nên không phải nhúng ở từng trang.
 */
export const useProfileStore = defineStore('profile', () => {
  const userId = ref<number | null>(null)

  function open(id: number) {
    userId.value = id
  }

  function close() {
    userId.value = null
  }

  return { userId, open, close }
})

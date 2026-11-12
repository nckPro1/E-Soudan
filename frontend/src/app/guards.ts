import type { Router } from 'vue-router'
import { useAuthStore } from '../features/auth/store'

/**
 * Router guard (ARCHITECTURE.md mục 11): kiểm tra đăng nhập, buộc đổi mật khẩu và quyền của route.
 * Đây chỉ điều hướng giao diện; mọi quyền thật vẫn được backend kiểm tra.
 */
export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    await auth.restore()

    if (to.meta.guestOnly) {
      return auth.isAuthenticated ? { name: 'home' } : true
    }

    if (!auth.isAuthenticated) {
      return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
    }

    if (auth.mustChangePassword && to.name !== 'change-password') {
      return { name: 'change-password' }
    }

    if (to.meta.permission && !auth.permissions.includes(to.meta.permission)) {
      return { name: 'home' }
    }

    return true
  })
}

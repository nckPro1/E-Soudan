import { computed } from 'vue'
import { useAuthStore } from '../../features/auth/store'
import type { SystemRole } from '../../features/auth/types'

/**
 * Đọc quyền của người dùng hiện tại để ẩn/hiện giao diện.
 * Đây chỉ là tiện lợi cho người dùng; backend vẫn là nơi quyết định (ARCHITECTURE.md mục 7).
 */
export function usePermission() {
  const auth = useAuthStore()

  const role = computed<SystemRole | null>(() => auth.user?.systemRole ?? null)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isManager = computed(() => role.value === 'MANAGER')

  function can(permission: string): boolean {
    return auth.permissions.includes(permission)
  }

  function hasRole(...roles: SystemRole[]): boolean {
    return role.value !== null && roles.includes(role.value)
  }

  return { role, isAdmin, isManager, can, hasRole }
}

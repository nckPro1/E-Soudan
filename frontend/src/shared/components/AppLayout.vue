<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BrandMark from './BrandMark.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'
import { useAuthStore } from '../../features/auth/store'
import { usePermission } from '../composables/usePermission'

// Khung bố cục sau đăng nhập: header + thanh bên theo quyền. Mục menu chỉ là tiện lợi, quyền thật nằm ở backend.
const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { can, isAdmin } = usePermission()

const navItems = computed(() =>
  [
    { name: 'home', path: '/', label: t('nav.home'), visible: true },
    { name: 'my-tasks', path: '/tasks/mine', label: t('nav.myTasks'), visible: !isAdmin.value },
    { name: 'teams', path: '/teams', label: t('nav.teams'), visible: true },
    { name: 'users', path: '/users', label: t('nav.users'), visible: can('USER_READ') },
  ].filter((item) => item.visible),
)

/** Mục menu sáng khi đang ở trang đó hoặc trang con của nó (ví dụ /teams/3 sáng mục Nhóm). */
function isActive(path: string): boolean {
  return path === '/' ? route.path === '/' : route.path === path || route.path.startsWith(`${path}/`)
}

async function signOut() {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="bar">
      <BrandMark />
      <div class="actions">
        <LanguageSwitcher />
        <span
          v-if="auth.user"
          class="who"
        >
          {{ auth.user.fullName }}
          <span class="role">{{ auth.user.systemRole }}</span>
        </span>
        <button
          type="button"
          class="signout"
          @click="signOut"
        >
          {{ t('auth.signOut') }}
        </button>
      </div>
    </header>

    <div class="frame">
      <nav
        class="side"
        :aria-label="t('nav.label')"
      >
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="link"
          :class="{ active: isActive(item.path) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.actions {
  display: flex;
  align-items: center;
  gap: 24px;
}

.who {
  font-size: 13px;
}

.role {
  margin-left: 8px;
  padding: 2px 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: var(--color-text-secondary);
}

.signout {
  height: 32px;
  padding: 0 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--color-text);
  cursor: pointer;
}

.signout:hover {
  background: #f0ede4;
}

.frame {
  display: flex;
  flex: 1;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
  width: 200px;
  padding: 16px 12px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

.link {
  padding: 8px 12px;
  border-left: 3px solid transparent;
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--color-text);
  text-decoration: none;
}

.link:hover {
  background: var(--color-surface-muted);
}

.link.active {
  border-left-color: var(--color-primary);
  background: var(--color-surface-muted);
  color: var(--color-primary);
  font-weight: 500;
}

.content {
  flex: 1;
  min-width: 0;
  padding: 32px;
}

@media (max-width: 720px) {
  .frame {
    flex-direction: column;
  }

  .side {
    flex-direction: row;
    width: auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
    overflow-x: auto;
  }

  .bar {
    padding: 12px 16px;
  }

  .who {
    display: none;
  }

  .content {
    padding: 16px;
  }
}
</style>

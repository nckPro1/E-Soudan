<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BrandMark from '../shared/components/BrandMark.vue'
import LanguageSwitcher from '../shared/components/LanguageSwitcher.vue'
import { useAuthStore } from '../features/auth/store'

// Trang chủ tạm sau đăng nhập. Khung bố cục chính (thanh bên, header) làm ở bước tiếp theo của Stage 1.13.
const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()

async function signOut() {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="page">
    <header class="bar">
      <BrandMark />
      <div class="actions">
        <LanguageSwitcher />
        <button
          type="button"
          class="signout"
          @click="signOut"
        >
          {{ t('auth.signOut') }}
        </button>
      </div>
    </header>

    <main class="body">
      <section
        v-if="auth.user"
        class="card"
      >
        <h1>{{ t('home.welcome', { name: auth.user.fullName }) }}</h1>
        <dl>
          <dt>{{ t('auth.email') }}</dt>
          <dd>{{ auth.user.email }}</dd>
          <dt>{{ t('home.role') }}</dt>
          <dd>{{ auth.user.systemRole }}</dd>
          <template v-if="auth.user.position">
            <dt>{{ t('home.position') }}</dt>
            <dd>{{ auth.user.position }}</dd>
          </template>
        </dl>
      </section>
    </main>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.actions {
  display: flex;
  align-items: center;
  gap: 24px;
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

.body {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 32px;
}

.card {
  max-width: 480px;
  padding: 32px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

h1 {
  margin: 0 0 24px;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

dl {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 8px 16px;
  margin: 0;
  font-size: 14px;
}

dt {
  color: var(--color-text-secondary);
}

dd {
  margin: 0;
}
</style>

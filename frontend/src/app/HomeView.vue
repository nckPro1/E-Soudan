<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../features/auth/store'

// Trang chủ tạm; nằm trong khung AppLayout. Sẽ thay bằng dashboard ở các stage sau.
const { t } = useI18n()
const auth = useAuthStore()
</script>

<template>
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
</template>

<style scoped>
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

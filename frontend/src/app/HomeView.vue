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
    class="card max-w-[30rem] rounded-lg bg-white p-8 shadow-sm ring-1 ring-black/5"
  >
    <h1 class="mb-6 text-xl font-bold">
      {{ t('home.welcome', { name: auth.user.fullName }) }}
    </h1>
    <dl class="grid grid-cols-[96px_1fr] gap-x-4 gap-y-2 text-sm">
      <dt class="text-[#616161]">
        {{ t('auth.email') }}
      </dt>
      <dd>{{ auth.user.email }}</dd>
      <dt class="text-[#616161]">
        {{ t('home.role') }}
      </dt>
      <dd>{{ auth.user.systemRole }}</dd>
      <template v-if="auth.user.position">
        <dt class="text-[#616161]">
          {{ t('home.position') }}
        </dt>
        <dd>{{ auth.user.position }}</dd>
      </template>
    </dl>
  </section>
</template>

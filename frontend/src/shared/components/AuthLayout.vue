<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BrandMark from './BrandMark.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'

const { t } = useI18n()
const year = new Date().getFullYear()
</script>

<template>
  <div class="page flex min-h-screen flex-col justify-between">
    <header class="top flex justify-end px-8 py-6">
      <LanguageSwitcher />
    </header>
    <main class="center flex grow items-center justify-center px-4 py-8">
      <div class="card w-full max-w-[25rem] rounded-lg bg-white p-10 shadow-sm ring-1 ring-black/5">
        <BrandMark class="brand mb-8" />
        <slot />
      </div>
    </main>
    <footer class="foot px-8 py-6 text-center text-xs tracking-wide text-[#616161]">
      {{ t('footer', { year }) }}
    </footer>
  </div>
</template>

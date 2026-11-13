<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LOCALES, LOCALE_LABELS, setLocale, type Locale } from '../../app/plugins/i18n'

const { locale } = useI18n()

function choose(value: Locale) {
  setLocale(value)
}
</script>

<template>
  <nav
    class="switcher flex items-center gap-2 text-xs tracking-wide text-[#616161]"
    aria-label="Language"
  >
    <template
      v-for="(item, index) in LOCALES"
      :key="item"
    >
      <span
        v-if="index > 0"
        class="sep text-[#e1dfdd]"
        aria-hidden="true"
      >|</span>
      <button
        type="button"
        class="item cursor-pointer border-0 border-b border-transparent bg-transparent pb-0.5 text-inherit hover:text-[#242424]"
        :class="{ 'active !cursor-default !border-b-[#242424] font-medium text-[#242424]': locale === item }"
        :aria-current="locale === item ? 'true' : undefined"
        :lang="item"
        @click="choose(item)"
      >
        {{ LOCALE_LABELS[item] }}
      </button>
    </template>
  </nav>
</template>

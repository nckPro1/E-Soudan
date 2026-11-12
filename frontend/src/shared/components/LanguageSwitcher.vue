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
    class="switcher"
    aria-label="Language"
  >
    <template
      v-for="(item, index) in LOCALES"
      :key="item"
    >
      <span
        v-if="index > 0"
        class="sep"
        aria-hidden="true"
      >|</span>
      <button
        type="button"
        class="item"
        :class="{ active: locale === item }"
        :aria-current="locale === item ? 'true' : undefined"
        :lang="item"
        @click="choose(item)"
      >
        {{ LOCALE_LABELS[item] }}
      </button>
    </template>
  </nav>
</template>

<style scoped>
.switcher {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  letter-spacing: 0.025em;
  color: var(--color-text-secondary);
}

.sep {
  color: var(--color-border);
}

.item {
  padding: 0 0 2px;
  background: none;
  border: 0;
  border-bottom: 1px solid transparent;
  color: inherit;
  font-size: inherit;
  letter-spacing: inherit;
  cursor: pointer;
}

.item:hover {
  color: var(--color-text);
}

.item.active {
  color: var(--color-text);
  font-weight: 500;
  border-bottom-color: var(--color-text);
  cursor: default;
}
</style>

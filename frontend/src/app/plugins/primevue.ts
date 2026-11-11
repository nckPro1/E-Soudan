import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'
import type { App } from 'vue'

// Màu chủ đạo lấy từ design system "Quiet Craft Teamwork" (chàm #274A78).
// Các bậc 50-950 là phỏng theo token trong design/; chỉnh lại khi hoàn thiện theme ở Stage 1.13.
const SoudanPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef3fa',
      100: '#d5e3ff',
      200: '#a7c8fe',
      300: '#7aa3d6',
      400: '#4f7ab0',
      500: '#3e5f8f',
      600: '#274a78',
      700: '#1f3c61',
      800: '#14294a',
      900: '#0e2140',
      950: '#083360',
    },
  },
})

export function installPrimeVue(app: App) {
  app.use(PrimeVue, {
    theme: {
      preset: SoudanPreset,
      options: { darkModeSelector: false },
    },
  })
}

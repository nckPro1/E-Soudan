import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { createAppRouter } from './router'
import { i18n } from './plugins/i18n'
import { installPrimeVue } from './plugins/primevue'
import { useAuthStore } from '../features/auth/store'
import 'primeicons/primeicons.css'
import '../shared/styles/tailwind.css'
import '../shared/styles/tokens.css'
import '../shared/styles/base.css'

const pinia = createPinia()
const router = createAppRouter()

const app = createApp(App).use(pinia).use(router).use(i18n)
installPrimeVue(app)

// Khi không làm mới được phiên (hết hạn, bị thu hồi, bị khoá) thì về trang đăng nhập kèm thông báo "hết phiên".
useAuthStore(pinia).setSessionExpiredHandler(() => {
  const current = router.currentRoute.value
  void router.replace({
    name: 'login',
    query: { reason: 'expired', ...(current.fullPath === '/' ? {} : { redirect: current.fullPath }) },
  })
})

app.mount('#app')

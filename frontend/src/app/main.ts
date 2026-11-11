import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { i18n } from './plugins/i18n'
import { installPrimeVue } from './plugins/primevue'
import 'primeicons/primeicons.css'

const app = createApp(App).use(createPinia()).use(router).use(i18n)
installPrimeVue(app)
app.mount('#app')

import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'

window.addEventListener('slotly:unauthorized', () => {
  const returnTo = `${window.location.pathname}${window.location.search}`
  if (window.location.pathname !== '/login') {
    void router.push({ name: 'login', query: { returnTo } })
  }
})

createApp(App).use(router).mount('#app')

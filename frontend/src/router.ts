import { createRouter, createWebHistory } from 'vue-router'
import { clearAuth, isTokenExpired, role, token } from './composables/auth'
import HomePage from './pages/HomePage.vue'
import LoginPage from './pages/LoginPage.vue'
import RegisterPage from './pages/RegisterPage.vue'
import PlacePage from './pages/PlacePage.vue'
import AppointmentsPage from './pages/AppointmentsPage.vue'
import OwnerDashboardPage from './pages/OwnerDashboardPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/login', name: 'login', component: LoginPage },
    { path: '/register', name: 'register', component: RegisterPage },
    { path: '/places/:id', name: 'place', component: PlacePage },
    { path: '/appointments', name: 'appointments', component: AppointmentsPage, meta: { requiresAuth: true } },
    { path: '/owner', name: 'owner', component: OwnerDashboardPage, meta: { requiresAuth: true, requiresOwner: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (token.value && isTokenExpired(token.value)) {
    clearAuth()
    return { name: 'login', query: { returnTo: to.fullPath } }
  }

  if (to.meta.requiresAuth && !token.value) {
    return { name: 'login', query: { returnTo: to.fullPath } }
  }

  if (to.meta.requiresOwner && role.value !== 'OWNER') {
    return { name: 'home' }
  }

  if (token.value && (to.name === 'login' || to.name === 'register')) {
    return { name: 'home' }
  }
})

export default router

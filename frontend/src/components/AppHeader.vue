<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { clearAuth, role, token } from '../composables/auth'
import { unreadOffers } from '../composables/waitlist'

withDefaults(defineProps<{ loginPath?: string }>(), { loginPath: '/login' })
const router = useRouter()

function logout(): void {
  clearAuth()
  void router.push('/')
}
</script>

<template>
  <header class="topbar">
    <RouterLink class="brand" to="/"><span class="brand-mark">S</span>slotly</RouterLink>
    <nav aria-label="Ana menü">
      <RouterLink to="/">Mekânlar</RouterLink>
      <RouterLink v-if="token && role === 'OWNER'" to="/owner">İşletme paneli</RouterLink>
      <template v-if="token && role === 'CUSTOMER'">
        <RouterLink to="/appointments">Randevularım</RouterLink>
        <RouterLink to="/waitlist">Bekleme listesi<span v-if="unreadOffers" class="notification-count">{{ unreadOffers }}</span></RouterLink>
      </template>
    </nav>
    <RouterLink v-if="token" to="/account">Hesap ayarları</RouterLink>
    <button v-if="token" class="button quiet" @click="logout">Çıkış</button>
    <RouterLink v-else class="button quiet" :to="loginPath">Giriş yap</RouterLink>
  </header>
</template>

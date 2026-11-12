<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthPageLayout from '../components/AuthPageLayout.vue'
import { role, saveAuth } from '../composables/auth'
import { request } from '../lib/api'
import { safeReturnTo } from '../lib/navigation'

const router = useRouter()
const username = ref('')
const password = ref('')
const busy = ref(false)
const errorMessage = ref('')

async function login(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''

  try {
    const response = await request<{ accessToken: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.value, password: password.value }),
    })

    saveAuth(response.accessToken)
    const returnTo = safeReturnTo(router.currentRoute.value.query.returnTo, role.value === 'OWNER' ? '/owner' : '/')
    await router.push(returnTo)
  } catch (error) {
    errorMessage.value = (error as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthPageLayout
    eyebrow="TEKRAR HOŞ GELDİN"
    title="Giriş yap"
    description="Randevularını ve planlarını kaldığın yerden takip et."
  >
    <form class="auth-form" @submit.prevent="login">
      <label>
        Kullanıcı adı
        <input
          v-model="username"
          type="text"
          required
          maxlength="40"
          autocomplete="username"
          placeholder="Kullanıcı adın"
        >
      </label>

      <label>
        Parola
        <input
          v-model="password"
          type="password"
          required
          minlength="10"
          maxlength="100"
          autocomplete="current-password"
          placeholder="Parolan"
        >
      </label>

      <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

      <button class="button primary auth-submit" type="submit" :disabled="busy">
        {{ busy ? 'Giriş yapılıyor…' : 'Giriş yap' }}
        <span aria-hidden="true">↗</span>
      </button>
    </form>

    <template #switch>
      <span>Slotly’de yeni misin?</span>
      <RouterLink :to="{ path: '/register', query: { returnTo: safeReturnTo(router.currentRoute.value.query.returnTo) } }">Hesap oluştur</RouterLink>
    </template>
  </AuthPageLayout>
</template>

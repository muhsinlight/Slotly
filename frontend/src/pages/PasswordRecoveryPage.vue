<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthPageLayout from '../components/AuthPageLayout.vue'
import { request } from '../lib/api'
import { clearAuth } from '../composables/auth'

const route = useRoute()
const router = useRouter()
const resetting = computed(() => route.path === '/reset-password')
const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || ''
if (resetting.value) void router.replace({ path: '/reset-password' })
const email = ref('')
const password = ref('')
const confirmation = ref('')
const busy = ref(false)
const message = ref('')
const error = ref('')

async function submit(): Promise<void> {
  if (busy.value) return
  error.value = ''
  if (resetting.value && password.value !== confirmation.value) {
    error.value = 'Parolalar eşleşmiyor.'
    return
  }
  busy.value = true
  try {
    const path = resetting.value ? '/auth/reset-password' : '/auth/forgot-password'
    const data = resetting.value ? { token, password: password.value } : { email: email.value }
    await request(path, { method: 'POST', body: JSON.stringify(data) })
    if (resetting.value) {
      clearAuth()
      password.value = ''
      confirmation.value = ''
      message.value = 'Parolan değiştirildi. Yeni parolanla giriş yapabilirsin.'
    } else {
      message.value = 'Adres doğrulanmış bir hesaba aitse sıfırlama bağlantısı gönderim kuyruğuna alındı. Yeniden istemek için bir dakika bekle.'
    }
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthPageLayout eyebrow="HESAP KURTARMA" :title="resetting ? 'Yeni parola belirle' : 'Parolamı unuttum'" description="Doğrulanmış e-posta adresinle hesabına yeniden eriş.">
    <p v-if="message" role="status">{{ message }}</p>
    <p v-else-if="resetting && !token" role="alert">Sıfırlama bağlantısı eksik. Yeni bağlantı iste.</p>
    <form v-else class="auth-form" @submit.prevent="submit">
      <template v-if="resetting">
        <label>Yeni parola<input v-model="password" type="password" autocomplete="new-password" minlength="10" maxlength="100" required :disabled="busy"></label>
        <label>Yeni parolayı tekrar yaz<input v-model="confirmation" type="password" autocomplete="new-password" minlength="10" maxlength="100" required :disabled="busy"></label>
      </template>
      <label v-else>E-posta<input v-model="email" type="email" autocomplete="email" maxlength="254" required :disabled="busy"></label>
      <button class="button primary" :disabled="busy">{{ busy ? 'İşleniyor…' : resetting ? 'Parolayı değiştir' : 'Sıfırlama bağlantısı iste' }}</button>
    </form>
    <p v-if="error" role="alert">{{ error }}</p>
    <RouterLink to="/login">Girişe dön</RouterLink>
  </AuthPageLayout>
</template>

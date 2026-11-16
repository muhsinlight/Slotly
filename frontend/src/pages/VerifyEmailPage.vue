<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthPageLayout from '../components/AuthPageLayout.vue'
import { request } from '../lib/api'

const router = useRouter()
const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || ''
// Remove the secret from browser history before any further navigation.
void router.replace({ path: '/verify-email' })
const busy = ref(false)
const done = ref(false)
const error = ref('')

async function verify(): Promise<void> {
  if (busy.value || done.value) return
  busy.value = true
  error.value = ''
  try {
    await request('/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) })
    done.value = true
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthPageLayout eyebrow="HESAP GÜVENLİĞİ" title="E-posta doğrulama" description="Adresini doğrulamak için aşağıdaki düğmeye bas.">
    <p v-if="done" role="status">E-posta adresin doğrulandı.</p>
    <p v-else-if="!token" role="alert">Doğrulama bağlantısı eksik. Hesap ayarlarından yeni bağlantı iste.</p>
    <button v-else class="button primary" :disabled="busy" @click="verify">{{ busy ? 'Doğrulanıyor…' : 'Adresimi doğrula' }}</button>
    <p v-if="error" role="alert">{{ error }}</p>
    <RouterLink to="/account">Hesap ayarlarına dön</RouterLink>
  </AuthPageLayout>
</template>

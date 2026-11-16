<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { request } from '../lib/api'

const email = ref('')
const verified = ref(false)
const busy = ref(false)
const message = ref('')
const error = ref('')

onMounted(async () => {
  try {
    const status = await request<{ email: string | null; verified: boolean }>('/account/email')
    email.value = status.email || ''
    verified.value = status.verified
  } catch (cause) {
    error.value = (cause as Error).message
  }
})

async function save(): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await request('/account/email', { method: 'POST', body: JSON.stringify({ email: email.value }) })
    verified.value = false
    message.value = 'Doğrulama bağlantısı gönderim kuyruğuna alındı. Bağlantı 30 dakika geçerlidir. Yeniden göndermek için bir dakika bekle.'
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppHeader />
  <main class="account-page">
    <h1>Hesap ayarları</h1>
    <p>E-posta adresini doğrulayarak hesap kurtarma ve bildirim özelliklerini kullanabilirsin.</p>
    <form @submit.prevent="save">
      <label>E-posta <input v-model="email" type="email" autocomplete="email" maxlength="254" required :disabled="busy"></label>
      <p>{{ verified ? 'Adres doğrulandı.' : 'Adres henüz doğrulanmadı.' }}</p>
      <button class="button primary" :disabled="busy">{{ busy ? 'Kaydediliyor…' : 'Kaydet ve doğrulama bağlantısı iste' }}</button>
    </form>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
  </main>
</template>

<style scoped>
.account-page { max-width: 640px; margin: 40px auto; padding: 24px; }
label { display: grid; gap: 8px; }
input { padding: 12px; border: 1px solid #aaa; border-radius: 8px; font: inherit; }
</style>

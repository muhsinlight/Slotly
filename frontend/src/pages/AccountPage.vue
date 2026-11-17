<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import NotificationSettings from '../components/NotificationSettings.vue'
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
  <div class="app-shell">
    <AppHeader />
    <main class="settings-page">
      <div class="settings-intro">
        <span class="eyebrow">HESABIN</span>
        <h1>Her şey kontrolünde.</h1>
        <p>İletişim bilgilerini ve bildirim tercihlerini buradan yönet.</p>
      </div>
      <div class="settings-layout">
        <aside class="settings-aside">
          <span class="settings-aside-icon" aria-hidden="true">✦</span>
          <h2>Sana özel bir alan.</h2>
          <p>E-posta adresini doğruladığında hesabını kolayca kurtarabilir, önemli randevu haberlerini alabilirsin.</p>
          <span class="settings-aside-foot">SLOTLY / HESAP AYARLARI</span>
        </aside>
        <div class="settings-content">
          <section class="settings-card">
            <div class="settings-card-heading"><span class="settings-index">01</span><div><h2>E-posta adresi</h2><p>Hesabınla ilgili haberler bu adrese gönderilir.</p></div></div>
            <form class="settings-form" @submit.prevent="save">
              <label>E-posta adresin <input v-model="email" type="email" autocomplete="email" maxlength="254" required :disabled="busy" placeholder="ornek@eposta.com"></label>
              <p class="verification-state" :class="{ verified }"><span aria-hidden="true">{{ verified ? '✓' : '!' }}</span>{{ verified ? 'Adres doğrulandı' : 'Adres henüz doğrulanmadı' }}</p>
              <button class="button primary" :disabled="busy">{{ busy ? 'Kaydediliyor…' : 'Kaydet ve doğrulama bağlantısı iste' }} <span aria-hidden="true">↗</span></button>
            </form>
            <p v-if="message" class="owner-notice" role="status">{{ message }}</p>
            <p v-if="error" class="booking-error" role="alert">{{ error }}</p>
          </section>
          <NotificationSettings :verified="verified" />
        </div>
      </div>
    </main>
  </div>
</template>

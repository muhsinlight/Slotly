<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../lib/api'

defineProps<{ verified: boolean }>()
const offerEmail = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')
const deliveries = ref<{ id: string; subject: string; state: string; createdAt: string }[]>([])
const labels: Record<string, string> = { PENDING: 'Gönderim bekliyor', SENT: 'Gönderildi', FAILED: 'Gönderilemedi', CANCELLED: 'Geçerliliğini yitirdi' }

onMounted(async () => {
  try {
    const preferences = await request<{ offerEmail: boolean }>('/account/notifications')
    offerEmail.value = preferences.offerEmail
    deliveries.value = await request('/account/notifications/deliveries')
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
    await request('/account/notifications', {
      method: 'PUT', body: JSON.stringify({ offerEmail: offerEmail.value, reminderEmail: false }),
    })
    message.value = 'Bildirim tercihlerin kaydedildi.'
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="notification-settings">
    <h2>E-posta bildirimleri</h2>
    <p>E-posta gönderimi henüz etkin değil. Tercihlerini şimdiden kaydedebilirsin; uygulama içi teklifler çalışmaya devam eder.</p>
    <p v-if="!verified">Bildirimleri açmak için önce e-posta adresini doğrula.</p>
    <form @submit.prevent="save">
      <label><input v-model="offerEmail" type="checkbox" :disabled="!verified || busy"> Boşalan saat tekliflerini e-posta ile al</label>
      <button class="button quiet" :disabled="busy">Tercihleri kaydet</button>
    </form>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <h3>Son bildirimler</h3>
    <ul v-if="deliveries.length">
      <li v-for="delivery in deliveries" :key="delivery.id">{{ delivery.subject }} · {{ labels[delivery.state] || delivery.state }} · {{ new Date(delivery.createdAt).toLocaleString('tr-TR') }}</li>
    </ul>
    <p v-else>Henüz bildirim bulunmuyor.</p>
  </section>
</template>

<style scoped>
.notification-settings { margin-top: 32px; padding-top: 24px; border-top: 1px solid #ccc; }
form { display: grid; gap: 16px; }
label { display: flex; align-items: center; gap: 8px; }
ul { padding-left: 20px; }
li { margin: 12px 0; }
</style>

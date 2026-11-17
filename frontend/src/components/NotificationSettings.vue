<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../lib/api'

defineProps<{ verified: boolean }>()
const offerEmail = ref(false)
const reminderEmail = ref(false)
const reminderHours = ref(24)
const busy = ref(false)
const error = ref('')
const message = ref('')
const deliveries = ref<{ id: string; subject: string; state: string; createdAt: string }[]>([])
const labels: Record<string, string> = { PENDING: 'Gönderim bekliyor', SENT: 'Gönderildi', FAILED: 'Gönderilemedi', CANCELLED: 'Geçerliliğini yitirdi' }

onMounted(async () => {
  try {
    const preferences = await request<{ offerEmail: boolean; reminderEmail: boolean; reminderHours: number }>('/account/notifications')
    offerEmail.value = preferences.offerEmail
    reminderEmail.value = preferences.reminderEmail
    reminderHours.value = preferences.reminderHours
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
      method: 'PUT', body: JSON.stringify({ offerEmail: offerEmail.value, reminderEmail: reminderEmail.value, reminderHours: reminderHours.value }),
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
      <label><input v-model="reminderEmail" type="checkbox" :disabled="!verified || busy"> Randevu hatırlatmalarını e-posta ile al</label>
      <label>Hatırlatma zamanı
        <select v-model.number="reminderHours" :disabled="!verified || !reminderEmail || busy">
          <option :value="24">24 saat önce</option>
          <option :value="2">2 saat önce</option>
        </select>
      </label>
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

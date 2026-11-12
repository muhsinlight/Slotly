<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { token } from '../composables/auth'
import { request } from '../lib/api'

type Place = { id: string; name: string; description: string | null; address: string; capacity: number }
type Offering = { id: string; name: string; description: string | null; price: number; durationMinutes: number | null; kind: 'SERVICE' | 'PRODUCT' }

const route = useRoute()
const router = useRouter()
const place = ref<Place | null>(null)
const offerings = ref<Offering[]>([])
const selectedOfferingId = ref('')
const selectedDate = ref(localDate())
const slots = ref<string[]>([])
const selectedSlot = ref('')
const loading = ref(true)
const slotsLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const notice = ref('')
const selectedOffering = computed(() => offerings.value.find((item) => item.id === selectedOfferingId.value) || null)

function localDate(): string {
  const date = new Date()
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 10)
}

function dayRange(date: string): { from: string; to: string } {
  const start = new Date(`${date}T00:00:00`)
  return { from: start.toISOString(), to: new Date(start.getTime() + 86_400_000).toISOString() }
}

async function loadPlace(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const id = String(route.params.id)
    const [placeResponse, offeringsResponse] = await Promise.all([
      request<Place>(`/places/${id}`),
      request<Offering[]>(`/places/${id}/offerings`),
    ])
    place.value = placeResponse
    offerings.value = offeringsResponse.filter((item) => item.kind === 'SERVICE')
    const requestedOffering = route.query.offering
    selectedOfferingId.value = offerings.value.some((item) => item.id === requestedOffering)
      ? String(requestedOffering)
      : offerings.value[0]?.id || ''
    if (typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date)) {
      selectedDate.value = route.query.date
    }
    const requestedSlot = typeof route.query.slot === 'string' ? route.query.slot : ''
    await loadSlots()
    if (requestedSlot && slots.value.includes(requestedSlot)) selectedSlot.value = requestedSlot
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

async function loadSlots(): Promise<void> {
  if (!place.value || !selectedOfferingId.value || !selectedDate.value) {
    slots.value = []
    return
  }

  slotsLoading.value = true
  error.value = ''
  selectedSlot.value = ''
  try {
    const { from, to } = dayRange(selectedDate.value)
    const query = new URLSearchParams({ offeringId: selectedOfferingId.value, from, to })
    slots.value = await request<string[]>(`/places/${place.value.id}/availability?${query}`)
  } catch (cause) {
    error.value = (cause as Error).message
    slots.value = []
  } finally {
    slotsLoading.value = false
  }
}

function loginPath(): string {
  const query = new URLSearchParams({
    offering: selectedOfferingId.value,
    date: selectedDate.value,
    ...(selectedSlot.value ? { slot: selectedSlot.value } : {}),
  })
  const returnTo = `/places/${place.value?.id}?${query}`
  return `/login?returnTo=${encodeURIComponent(returnTo)}`
}

async function confirmBooking(): Promise<void> {
  if (!place.value || !selectedOffering.value || !selectedSlot.value) return
  if (!token.value) {
    await router.push(loginPath())
    return
  }

  submitting.value = true
  error.value = ''
  try {
    const { from, to } = dayRange(selectedDate.value)
    const query = new URLSearchParams({ offeringId: selectedOffering.value.id, from, to })
    const latestSlots = await request<string[]>(`/places/${place.value.id}/availability?${query}`)
    if (!latestSlots.includes(selectedSlot.value)) {
      slots.value = latestSlots
      selectedSlot.value = ''
      error.value = 'Bu saat az önce doldu. Lütfen yeni bir saat seç.'
      return
    }

    await request('/appointments', {
      method: 'POST',
      body: JSON.stringify({ placeId: place.value.id, offeringId: selectedOffering.value.id, startsAt: selectedSlot.value }),
    })
    notice.value = 'Randevun oluşturuldu.'
    await router.push({ name: 'appointments', query: { created: '1' } })
  } catch (cause) {
    error.value = (cause as Error).message
    await loadSlots()
  } finally {
    submitting.value = false
  }
}

watch(selectedOfferingId, () => { void loadSlots() })
watch(selectedDate, () => { void loadSlots() })
onMounted(loadPlace)
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/"><span class="brand-mark">S</span>slotly</RouterLink>
      <nav><RouterLink to="/">Mekânlar</RouterLink><RouterLink v-if="token" to="/appointments">Randevularım</RouterLink></nav>
      <RouterLink v-if="!token" class="button quiet" :to="loginPath()">Giriş yap</RouterLink>
    </header>
    <main class="section booking-page">
      <RouterLink to="/" class="text-button">← Mekânlara dön</RouterLink>
      <p v-if="loading" class="empty" role="status">Mekân bilgileri yükleniyor…</p>
      <p v-else-if="error && !place" class="booking-error" role="alert">{{ error }}</p>
      <template v-else-if="place">
        <div class="booking-heading">
          <span class="eyebrow">{{ place.address }}</span>
          <h1>{{ place.name }}</h1>
          <p>{{ place.description || 'Hizmet seçip sana uygun bir zaman bul.' }}</p>
        </div>
        <div class="booking-layout">
          <section class="booking-card">
            <h2>1. Hizmet seç</h2>
            <label class="booking-field">Hizmet
              <select v-model="selectedOfferingId">
                <option v-for="offering in offerings" :key="offering.id" :value="offering.id">
                  {{ offering.name }} · {{ offering.durationMinutes }} dk · {{ offering.price.toFixed(2) }} ₺
                </option>
              </select>
            </label>
            <p v-if="!offerings.length" class="empty">Bu mekânda şu anda randevu alınabilecek hizmet yok.</p>
            <label class="booking-field">Tarih
              <input v-model="selectedDate" type="date" :min="localDate()" :disabled="!selectedOffering">
            </label>
            <h2>2. Saat seç</h2>
            <p v-if="slotsLoading" class="empty" role="status">Uygun saatler aranıyor…</p>
            <div v-else-if="slots.length" class="booking-slots">
              <button v-for="slot in slots" :key="slot" class="slot" :class="{ selected: selectedSlot === slot }" @click="selectedSlot = slot">
                {{ new Date(slot).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) }}
              </button>
            </div>
            <p v-else-if="selectedOffering" class="empty">Bu tarihte uygun saat bulunamadı. Başka bir gün seç.</p>
            <p v-if="error && place" class="booking-error" role="alert">{{ error }}</p>
          </section>
          <aside class="booking-card booking-summary">
            <h2>Randevu özeti</h2>
            <dl>
              <div><dt>İşletme</dt><dd>{{ place.name }}</dd></div>
              <div><dt>Hizmet</dt><dd>{{ selectedOffering?.name || 'Seçilmedi' }}</dd></div>
              <div><dt>Tarih</dt><dd>{{ new Date(`${selectedDate}T12:00:00`).toLocaleDateString('tr-TR', { dateStyle: 'long' }) }}</dd></div>
              <div><dt>Saat</dt><dd>{{ selectedSlot ? new Date(selectedSlot).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : 'Seçilmedi' }}</dd></div>
              <div><dt>Süre</dt><dd>{{ selectedOffering?.durationMinutes ?? '—' }} dk</dd></div>
              <div><dt>Ücret</dt><dd>{{ selectedOffering ? `${selectedOffering.price.toFixed(2)} ₺` : '—' }}</dd></div>
            </dl>
            <button class="button primary booking-confirm" :disabled="!selectedOffering || !selectedSlot || submitting" @click="confirmBooking">
              {{ submitting ? 'Kontrol ediliyor…' : token ? 'Randevuyu onayla' : 'Giriş yap ve devam et' }}
            </button>
            <small v-if="!token">Seçimin girişten sonra korunacak.</small>
          </aside>
        </div>
      </template>
    </main>
  </div>
</template>

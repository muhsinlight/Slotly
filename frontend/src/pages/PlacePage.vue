<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { role, token } from '../composables/auth'
import { ApiError } from '../lib/api'
import { createAppointment, getPlace, joinWaitlist, listOfferings, listSlots, localDate, type Offering, type Place } from '../lib/bookings'

const route = useRoute()
const router = useRouter()
const place = ref<Place | null>(null)
const offerings = ref<Offering[]>([])
const selectedOfferingId = ref('')
const selectedDate = ref(localDate())
const slots = ref<string[]>([])
const selectedSlot = ref('')
const desiredTime = ref('09:00')
const loading = ref(true)
const slotsLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const slotError = ref('')
const selectedOffering = computed(() => offerings.value.find((item) => item.id === selectedOfferingId.value) || null)
const customerAllowed = computed(() => !token.value || role.value === 'CUSTOMER')
let ready = false
let loadVersion = 0
let slotController: AbortController | undefined

function selectionPath(): string {
  const query = new URLSearchParams({
    offering: selectedOfferingId.value,
    date: selectedDate.value,
    wait: desiredTime.value,
    ...(selectedSlot.value ? { slot: selectedSlot.value } : {}),
  })
  return `/places/${route.params.id}?${query}`
}

function loginPath(): string {
  return `/login?returnTo=${encodeURIComponent(selectionPath())}`
}

async function rememberSelection(): Promise<void> {
  await router.replace(selectionPath())
}

async function loadPlace(): Promise<void> {
  const version = ++loadVersion
  ready = false
  loading.value = true
  error.value = ''
  try {
    const id = String(route.params.id)
    const [placeResponse, offeringsResponse] = await Promise.all([getPlace(id), listOfferings(id)])
    if (version !== loadVersion) return
    place.value = placeResponse
    offerings.value = offeringsResponse.filter((item) => item.kind === 'SERVICE')
    selectedOfferingId.value = offerings.value.some((item) => item.id === route.query.offering)
      ? String(route.query.offering) : offerings.value[0]?.id || ''
    if (typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) && route.query.date >= localDate()) {
      selectedDate.value = route.query.date
    }
    if (typeof route.query.wait === 'string' && /^\d{2}:\d{2}$/.test(route.query.wait)) {
      desiredTime.value = route.query.wait
    }
    const restoredSlot = typeof route.query.slot === 'string' ? route.query.slot : ''
    ready = true
    await loadSlots(restoredSlot)
  } catch (cause) {
    if (version === loadVersion) error.value = (cause as Error).message
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function loadSlots(preserveSlot = ''): Promise<void> {
  slotController?.abort()
  slots.value = []
  selectedSlot.value = ''
  if (!place.value || !selectedOfferingId.value || !selectedDate.value) return
  const controller = new AbortController()
  slotController = controller
  slotsLoading.value = true
  slotError.value = ''
  try {
    const result = await listSlots(place.value.id, selectedOfferingId.value, selectedDate.value, controller.signal)
    if (controller.signal.aborted) return
    slots.value = result
    if (preserveSlot && result.includes(preserveSlot)) selectedSlot.value = preserveSlot
    else if (preserveSlot) error.value = 'Seçtiğin saat artık uygun değil. Lütfen yeni bir saat seç.'
  } catch (cause) {
    if (!controller.signal.aborted) slotError.value = (cause as Error).message
  } finally {
    if (!controller.signal.aborted) slotsLoading.value = false
  }
}

async function chooseSlot(slot: string): Promise<void> {
  selectedSlot.value = slot
  error.value = ''
  await rememberSelection()
}

async function confirmBooking(): Promise<void> {
  if (submitting.value || !place.value || !selectedOffering.value || !selectedSlot.value || !customerAllowed.value) return
  submitting.value = true
  error.value = ''
  try {
    await rememberSelection()
    if (!token.value) {
      await router.push(loginPath())
      return
    }
    const latestSlots = await listSlots(place.value.id, selectedOfferingId.value, selectedDate.value)
    if (!latestSlots.includes(selectedSlot.value)) {
      slots.value = latestSlots
      selectedSlot.value = ''
      error.value = 'Bu saat az önce doldu. Lütfen yeni bir saat seç.'
      return
    }
    await createAppointment({ placeId: place.value.id, offeringId: selectedOfferingId.value, startsAt: selectedSlot.value })
    await router.push({ name: 'appointments', query: { created: '1' } })
  } catch (cause) {
    error.value = cause instanceof ApiError && cause.status === 409
      ? 'Bu saat az önce doldu. Lütfen yeni bir saat seç.' : (cause as Error).message
    await loadSlots()
  } finally {
    submitting.value = false
  }
}

async function waitForTime(): Promise<void> {
  if (submitting.value || !place.value || !selectedOffering.value || !desiredTime.value || !customerAllowed.value) return
  submitting.value = true
  error.value = ''
  try {
    await rememberSelection()
    if (!token.value) {
      await router.push(loginPath())
      return
    }
    await joinWaitlist({
      placeId: place.value.id,
      offeringId: selectedOfferingId.value,
      startsAt: new Date(`${selectedDate.value}T${desiredTime.value}:00`).toISOString(),
    })
    await router.push('/waitlist')
  } catch (cause) {
    const message = (cause as Error).message
    error.value = message === 'The requested time is currently available'
      ? 'Bu saat şu anda müsait. Uygun saatlerden seçerek doğrudan randevu alabilirsin.'
      : message === 'You are already waiting for this time'
        ? 'Bu saat için zaten sıradasın. Bekleme listenden durumunu takip edebilirsin.'
        : cause instanceof ApiError && cause.status === 400
          ? 'Bu saat hizmetin çalışma aralığına uymuyor. Gelecekteki bir hizmet başlangıç saati seç.'
          : message
  } finally {
    submitting.value = false
  }
}

watch([selectedOfferingId, selectedDate], () => {
  if (!ready) return
  error.value = ''
  void loadSlots()
}, { flush: 'sync' })
watch(() => route.params.id, () => { void loadPlace() })
onMounted(loadPlace)
onBeforeUnmount(() => {
  loadVersion++
  slotController?.abort()
})
</script>

<template>
  <div class="app-shell">
    <AppHeader :login-path="loginPath()" />
    <main class="section booking-page">
      <RouterLink to="/" class="text-button">← Mekânlara dön</RouterLink>
      <p v-if="loading" class="empty" role="status">Mekân bilgileri yükleniyor…</p>
      <p v-else-if="error && !place" class="booking-error" role="alert">{{ error }} <button class="text-button" @click="loadPlace">Yeniden dene</button></p>
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
              <select v-model="selectedOfferingId" :disabled="submitting">
                <option v-for="offering in offerings" :key="offering.id" :value="offering.id">
                  {{ offering.name }} · {{ offering.durationMinutes }} dk · {{ offering.price.toFixed(2) }} ₺
                </option>
              </select>
            </label>
            <p v-if="!offerings.length" class="empty">Bu mekânda şu anda randevu alınabilecek hizmet yok.</p>
            <label class="booking-field">Tarih
              <input v-model="selectedDate" type="date" :min="localDate()" :disabled="!selectedOffering || submitting">
            </label>
            <h2>2. Saat seç</h2>
            <p v-if="slotsLoading" class="empty" role="status">Uygun saatler aranıyor…</p>
            <div v-else-if="slots.length" class="booking-slots">
              <button v-for="slot in slots" :key="slot" class="slot" :class="{ selected: selectedSlot === slot }" :aria-pressed="selectedSlot === slot" :disabled="submitting" @click="chooseSlot(slot)">
                {{ new Date(slot).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) }}
              </button>
            </div>
            <p v-else-if="selectedOffering" class="empty">Bu tarihte uygun saat bulunamadı. Başka bir gün seç.</p>
            <p v-if="error && place" class="booking-error" role="alert">{{ error }}</p>
            <p v-if="slotError" class="booking-error" role="alert">{{ slotError }} <button class="text-button" @click="loadSlots()">Yeniden dene</button></p>
            <form v-if="selectedOffering && customerAllowed" class="waitlist-join" @submit.prevent="waitForTime">
              <p>İstediğin saat dolu mu? Boşalırsa sırana göre teklif alabilirsin.</p>
              <label>Beklemek istediğin saat<input v-model="desiredTime" type="time" required :disabled="submitting"></label>
              <button class="text-button" :disabled="submitting || slotsLoading">{{ submitting ? 'Gönderiliyor…' : 'Bekleme listesine katıl' }}</button>
            </form>
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
            <button class="button primary booking-confirm" :disabled="!selectedOffering || !selectedSlot || submitting || slotsLoading || !customerAllowed" @click="confirmBooking">
              {{ submitting ? 'Kontrol ediliyor…' : token ? 'Randevuyu onayla' : 'Giriş yap ve devam et' }}
            </button>
            <small v-if="!token">Seçimin girişten sonra korunacak.</small>
            <small v-else-if="!customerAllowed">Randevu almak için müşteri hesabını kullan.</small>
          </aside>
        </div>
      </template>
    </main>
  </div>
</template>

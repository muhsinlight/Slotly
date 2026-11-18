<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { role, token } from '../composables/auth'
import { ApiError } from '../lib/api'
import { createAppointment, getPlace, joinWaitlist, listOfferings, listSlots, type Offering, type Place } from '../lib/bookings'
import { businessDate, possibleStarts, timeFormatter } from '../lib/businessTime'

const route = useRoute()
const router = useRouter()
const place = ref<Place | null>(null)
const offerings = ref<Offering[]>([])
const selectedOfferingId = ref('')
const selectedDate = ref('')
const slots = ref<string[]>([])
const selectedSlot = ref('')
const desiredTime = ref('09:00')
const desiredOffset = ref('')
const loading = ref(true)
const slotsLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const slotError = ref('')
const services = computed(() => offerings.value.filter(item => item.kind === 'SERVICE'))
const selectedOffering = computed(() => services.value.find((item) => item.id === selectedOfferingId.value) || null)
const customerAllowed = computed(() => !token.value || role.value === 'CUSTOMER')
const waitTimes = computed(() => {
  if (!place.value || !selectedDate.value || !desiredTime.value) return []
  try {
    return possibleStarts(selectedDate.value, desiredTime.value, place.value.timeZone)
  } catch {
    return []
  }
})
const requestedTime = computed(() => waitTimes.value.find(time => time.offset === desiredOffset.value) || waitTimes.value[0])
const slotFormat = computed(() => place.value && slots.value[0]
  ? timeFormatter(slots.value[0], place.value.timeZone) : undefined)
const startingPrice = computed(() => offerings.value.length
  ? Math.min(...offerings.value.map((offering) => offering.price)) : null)
const money = (value: number) => new Intl.NumberFormat('tr-TR', {
  style: 'currency', currency: 'TRY', minimumFractionDigits: 0, maximumFractionDigits: 2,
}).format(value)
let ready = false
let loadVersion = 0
let slotController: AbortController | undefined

function selectionPath(): string {
  const query = new URLSearchParams({
    offering: selectedOfferingId.value,
    date: selectedDate.value,
    wait: desiredTime.value,
    ...(waitTimes.value.length > 1 && requestedTime.value ? { offset: requestedTime.value.offset } : {}),
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
  slotController?.abort()
  ready = false
  loading.value = true
  place.value = null
  error.value = ''
  try {
    const id = String(route.params.id)
    const [placeResponse, offeringsResponse] = await Promise.all([getPlace(id), listOfferings(id)])
    if (version !== loadVersion) return
    place.value = placeResponse
    offerings.value = offeringsResponse.filter((item) => item.active)
    selectedOfferingId.value = services.value.some((item) => item.id === route.query.offering)
      ? String(route.query.offering) : services.value[0]?.id || ''
    const today = businessDate(placeResponse.timeZone)
    selectedDate.value = typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) && route.query.date >= today
      ? route.query.date : today
    desiredTime.value = typeof route.query.wait === 'string' && /^\d{2}:\d{2}$/.test(route.query.wait)
      ? route.query.wait : '09:00'
    desiredOffset.value = typeof route.query.offset === 'string' ? route.query.offset : ''
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
  slotsLoading.value = false
  slotError.value = ''
  if (!place.value || !selectedOfferingId.value || !selectedDate.value) return
  const controller = new AbortController()
  slotController = controller
  slotsLoading.value = true
  try {
    const result = await listSlots(place.value, selectedOfferingId.value, selectedDate.value, controller.signal)
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
    const latestSlots = await listSlots(place.value, selectedOfferingId.value, selectedDate.value)
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
  const requested = requestedTime.value
  if (!requested) {
    error.value = 'Bu tarih ve saat işletmenin saat diliminde geçerli değil. Başka bir saat seç.'
    return
  }
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
      startsAt: requested.instant,
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
          <div class="place-intro">
            <span class="place-monogram" aria-hidden="true">{{ place.name.charAt(0) }}</span>
            <div>
              <span class="eyebrow">{{ place.address }}</span>
              <h1>{{ place.name }}</h1>
              <p>{{ place.description || 'Hizmet seçip sana uygun bir zaman bul.' }}</p>
              <div class="place-facts">
                <span>{{ services.length }} hizmet</span>
                <span v-if="startingPrice !== null">{{ money(startingPrice) }}'den başlayan fiyatlar</span>
                <span>Online randevu</span>
              </div>
            </div>
          </div>
          <div class="place-intro-note">
            <span class="eyebrow">SANA AYRILAN ZAMAN</span>
            <h2>Gününü planla,<br>kendine yer aç.</h2>
            <p>Hizmetini seç, uygun saatleri gör ve randevunu tek yerden takip et.</p>
          </div>
        </div>
        <ol class="booking-progress" aria-label="Randevu adımları">
          <li :class="{ complete: selectedOffering }"><span>01</span><div><b>Hizmetini seç</b><small>Süre ve fiyatları karşılaştır</small></div></li>
          <li :class="{ complete: selectedSlot }"><span>02</span><div><b>Zamanını belirle</b><small>Sana uygun gün ve saati bul</small></div></li>
          <li><span>03</span><div><b>Randevunu onayla</b><small>Randevularım ekranından takip et</small></div></li>
        </ol>
        <div class="booking-layout">
          <section class="booking-card">
            <h2>1. Hizmet seç</h2>
            <label class="booking-field">Hizmet
              <select v-model="selectedOfferingId" :disabled="submitting">
                <option v-for="offering in services" :key="offering.id" :value="offering.id">
                  {{ offering.name }} · {{ offering.durationMinutes }} dk · {{ offering.price.toFixed(2) }} ₺
                </option>
              </select>
            </label>
            <p v-if="!services.length" class="empty">Bu mekânda şu anda randevu alınabilecek hizmet yok.</p>
            <label class="booking-field">Tarih
              <input v-model="selectedDate" type="date" :min="businessDate(place.timeZone)" :disabled="!selectedOffering || submitting">
            </label>
            <h2>2. Saat seç</h2>
            <p class="empty">İşletmenin saat dilimi · {{ place.timeZone }}</p>
            <p v-if="slotsLoading" class="empty" role="status">Uygun saatler aranıyor…</p>
            <div v-else-if="slots.length" class="booking-slots">
              <button v-for="slot in slots" :key="slot" class="slot" :class="{ selected: selectedSlot === slot }" :aria-pressed="selectedSlot === slot" :disabled="submitting" @click="chooseSlot(slot)">
                {{ slotFormat?.format(Date.parse(slot)) }}
              </button>
            </div>
            <p v-else-if="selectedOffering" class="empty">Bu tarihte uygun saat bulunamadı. Başka bir gün seç.</p>
            <p v-if="error && place" class="booking-error" role="alert">{{ error }}</p>
            <p v-if="slotError" class="booking-error" role="alert">{{ slotError }} <button class="text-button" @click="loadSlots()">Yeniden dene</button></p>
            <form v-if="selectedOffering && customerAllowed" class="waitlist-join" @submit.prevent="waitForTime">
              <p>İstediğin saat dolu mu? Boşalırsa sırana göre teklif alabilirsin.</p>
              <label>Beklemek istediğin saat<input v-model="desiredTime" type="time" required :disabled="submitting"></label>
              <label v-if="waitTimes.length > 1">Aynı saat bu tarihte iki kez yaşanıyor
                <select :value="requestedTime?.offset" :disabled="submitting" @change="desiredOffset = ($event.target as HTMLSelectElement).value">
                  <option v-for="time in waitTimes" :key="time.offset" :value="time.offset">{{ desiredTime }} (UTC{{ time.offset }})</option>
                </select>
              </label>
              <button class="text-button" :disabled="submitting || slotsLoading">{{ submitting ? 'Gönderiliyor…' : 'Bekleme listesine katıl' }}</button>
            </form>
          </section>
          <div class="booking-sidebar">
          <aside class="booking-card booking-summary">
            <h2>Randevu özeti</h2>
            <dl>
              <div><dt>İşletme</dt><dd>{{ place.name }}</dd></div>
              <div><dt>Hizmet</dt><dd>{{ selectedOffering?.name || 'Seçilmedi' }}</dd></div>
              <div><dt>Tarih</dt><dd>{{ selectedDate ? new Date(`${selectedDate}T12:00:00Z`).toLocaleDateString('tr-TR', { dateStyle: 'long', timeZone: 'UTC' }) : 'Seçilmedi' }}</dd></div>
              <div><dt>Saat</dt><dd>{{ selectedSlot ? slotFormat?.format(Date.parse(selectedSlot)) : 'Seçilmedi' }}</dd></div>
              <div><dt>Süre</dt><dd>{{ selectedOffering?.durationMinutes ?? '—' }} dk</dd></div>
              <div><dt>Ücret</dt><dd>{{ selectedOffering ? `${selectedOffering.price.toFixed(2)} ₺` : '—' }}</dd></div>
            </dl>
            <button class="button primary booking-confirm" :disabled="!selectedOffering || !selectedSlot || submitting || slotsLoading || !customerAllowed" @click="confirmBooking">
              {{ submitting ? 'Kontrol ediliyor…' : token ? 'Randevuyu onayla' : 'Giriş yap ve devam et' }}
            </button>
            <small v-if="!token">Seçimin girişten sonra korunacak.</small>
            <small v-else-if="!customerAllowed">Randevu almak için müşteri hesabını kullan.</small>
          </aside>
          <section class="booking-help">
            <span class="eyebrow">PLANLAR DEĞİŞEBİLİR</span>
            <h2>İstediğin saat doluysa sıranı al.</h2>
            <p>Yer açıldığında sırana göre teklif gelir. Teklifini en geç 30 dakika içinde ve randevu başlamadan kabul edebilirsin.</p>
            <div><span aria-hidden="true">↗</span> Tekliflerini Bekleme listesi ekranından takip edebilirsin.</div>
          </section>
          </div>
        </div>
        <section v-if="offerings.length" class="place-services" aria-labelledby="place-services-title">
          <div class="place-services-heading">
            <h2 id="place-services-title">Hizmet ve ürün fiyatları</h2>
          </div>
          <div class="place-service-grid">
            <article v-for="offering in offerings" :key="offering.id" class="place-service-tile" :class="{ chosen: selectedOfferingId === offering.id }">
              <span class="service-tile-top"><span>{{ offering.kind === 'SERVICE' ? `${offering.durationMinutes} dk` : 'Ürün' }}</span></span>
              <strong>{{ offering.name }}</strong>
              <span v-if="offering.description" class="service-tile-description">{{ offering.description }}</span>
              <b>{{ money(offering.price) }}</b>
              <button v-if="offering.kind === 'SERVICE'" type="button" class="text-button" :aria-pressed="selectedOfferingId === offering.id" :disabled="submitting" @click="selectedOfferingId = offering.id">{{ selectedOfferingId === offering.id ? 'Seçili ✓' : 'Hizmeti seç ↗' }}</button>
            </article>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.booking-page {
  padding-top: 32px;
}
.booking-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
  padding: 28px;
  border: 1px solid #ded3f7;
  border-radius: 12px;
  background: linear-gradient(115deg, #f1e9ff, #e8f3ff);
  margin: 20px 0;
}
.place-intro {
  display: flex;
  align-items: center;
  gap: 22px;
  min-width: 0;
}
.place-monogram {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 88px;
  height: 104px;
  border-radius: 44px 44px 12px 12px;
  background: #e4d4ff;
  color: var(--green);
  font: 600 48px Manrope, sans-serif;
}
.booking-heading .eyebrow {
  margin-bottom: 10px;
}
.booking-heading h1 {
  font-size: clamp(28px, 3.5vw, 42px);
  overflow-wrap: anywhere;
}
.booking-heading p {
  font-size: 13px;
  margin: 12px 0;
}
.place-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.place-facts span {
  padding: 5px 10px;
  background: #ffffffb3;
  border-radius: 20px;
  color: #6547a8;
  font-size: 11px;
}
.place-intro-note {
  flex: 0 0 230px;
  padding-left: 28px;
  border-left: 1px solid #d9c9f3;
}
.place-intro-note h2 {
  margin: 0;
  color: var(--green);
  font: 600 23px/1.4 Manrope, sans-serif;
  letter-spacing: -.6px;
}
.booking-progress {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  list-style: none;
  padding: 0;
  margin: 0 0 24px;
}
.booking-progress li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}
.booking-progress li > span {
  color: #7b7192;
  background: #f1ebfb;
  border: 1px solid var(--line);
  border-radius: 50%;
  padding: 10px;
  font-size: 12px;
}
.booking-progress .complete > span {
  background: var(--green);
  color: white;
}
.booking-progress b, .booking-progress small {
  display: block;
}
.booking-progress b {
  font-size: 13px;
  font-weight: 500;
}
.booking-progress small {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}
.booking-sidebar {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.booking-help {
  padding: 24px;
  background: #f1ebff;
  border: 1px solid #ded3f5;
  border-radius: 16px;
}
.booking-help .eyebrow {
  margin-bottom: 10px;
}
.booking-help h2 {
  margin: 0;
  font: 600 18px/1.5 Manrope, sans-serif;
  color: #3d286e;
}
.booking-help p, .booking-help div {
  font-size: 12px;
  line-height: 1.8;
  color: #66577f;
}
.booking-help div {
  padding-top: 12px;
  border-top: 1px solid #ded3f5;
}
.place-services {
  margin-top: 36px;
}
.place-services-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
  margin-bottom: 20px;
}
.place-services-heading .eyebrow {
  margin-bottom: 8px;
}
.place-services-heading h2 {
  margin: 0;
  font: 600 24px Manrope, sans-serif;
  letter-spacing: -.6px;
}
.place-services-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.place-service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.place-service-tile {
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: 14px;
  text-align: left;
  padding: 22px;
  background: white;
  color: var(--ink);
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow-wrap: anywhere;
}
.place-service-tile.chosen {
  border-color: #ad95ef;
  background: #f7f2ff;
}
.place-service-tile:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: 3px;
}
.service-tile-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  font-size: 11px;
  color: var(--green);
}
.place-service-tile strong {
  font: 600 17px Manrope, sans-serif;
}
.service-tile-description {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.place-service-tile > b {
  margin-top: auto;
  font-size: 18px;
  color: var(--green);
}
@media (max-width: 1000px) {
  .place-intro-note {
    display: none;
  }
}
@media (max-width: 600px) {
  .booking-heading {
    padding: 20px;
  }
  .place-monogram {
    display: none;
  }
  .booking-progress {
    gap: 8px;
  }
  .booking-progress li {
    flex-direction: column;
    align-items: start;
    gap: 8px;
  }
  .booking-progress b {
    font-size: 11px;
  }
  .booking-progress small {
    display: none;
  }
  .place-service-grid {
    grid-template-columns: 1fr;
  }
  .place-services-heading {
    display: block;
  }
  .place-services-heading p {
    margin-top: 10px;
  }
}
</style>

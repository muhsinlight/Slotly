<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import CalendarExceptions from '../components/CalendarExceptions.vue'
import { request } from '../lib/api'

type Place = { id: string; name: string; description: string | null; address: string; capacity: number; active: boolean }
type Offering = { id: string; placeId: string; name: string; description: string | null; price: number; durationMinutes: number | null; kind: 'SERVICE' | 'PRODUCT'; active: boolean }
type BusinessHour = { id: string; dayOfWeek: string; opensAt: string; closesAt: string }
type Appointment = { id: string; placeName: string; offeringName: string; customerName: string; startsAt: string; endsAt: string; status: string }

const days = [
  { value: 'MONDAY', label: 'Pazartesi' }, { value: 'TUESDAY', label: 'Salı' },
  { value: 'WEDNESDAY', label: 'Çarşamba' }, { value: 'THURSDAY', label: 'Perşembe' },
  { value: 'FRIDAY', label: 'Cuma' }, { value: 'SATURDAY', label: 'Cumartesi' },
  { value: 'SUNDAY', label: 'Pazar' },
]
const places = ref<Place[]>([])
const selectedPlaceId = ref('')
const offerings = ref<Offering[]>([])
const hours = ref<BusinessHour[]>([])
const appointments = ref<Appointment[]>([])
const date = ref(localDate())
const calendarView = ref<'day' | 'week'>('day')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const placeForm = ref({ name: '', description: '', address: '', capacity: 1, active: true })
const hourForm = ref({ dayOfWeek: 'MONDAY', opensAt: '09:00', closesAt: '17:00' })
const offerForm = ref({ id: '', name: '', description: '', price: 0, durationMinutes: 30, kind: 'SERVICE' as 'SERVICE' | 'PRODUCT', active: true })
const selectedPlace = computed(() => places.value.find((place) => place.id === selectedPlaceId.value) || null)
const isEditingOffer = computed(() => Boolean(offerForm.value.id))

function localDate(): string {
  const current = new Date()
  current.setMinutes(current.getMinutes() - current.getTimezoneOffset())
  return current.toISOString().slice(0, 10)
}

function dayRange(): { from: string; to: string } {
  const start = new Date(`${date.value}T00:00:00`)
  if (calendarView.value === 'week') {
    start.setDate(start.getDate() - (start.getDay() + 6) % 7)
  }
  const end = new Date(start)
  end.setDate(end.getDate() + (calendarView.value === 'week' ? 7 : 1))
  return { from: start.toISOString(), to: end.toISOString() }
}

function setMessage(value: string): void {
  notice.value = value
  error.value = ''
}

async function loadPlaces(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    places.value = await request<Place[]>('/owner/places')
    if (!places.value.some((place) => place.id === selectedPlaceId.value)) {
      selectedPlaceId.value = places.value[0]?.id || ''
    }
    if (!places.value.length) {
      loading.value = false
    }
  } catch (cause) {
    error.value = (cause as Error).message
    loading.value = false
  }
}

async function loadPlaceData(): Promise<void> {
  const place = selectedPlace.value
  if (!place) {
    offerings.value = []
    hours.value = []
    appointments.value = []
    return
  }

  const range = dayRange()
  const query = new URLSearchParams({ placeId: place.id, from: range.from, to: range.to })
  try {
    const [offerList, hourList, appointmentList] = await Promise.all([
      request<Offering[]>(`/owner/places/${place.id}/offerings`),
      request<BusinessHour[]>(`/places/${place.id}/business-hours`),
      request<Appointment[]>(`/owner/appointments?${query}`),
    ])
    offerings.value = offerList
    hours.value = hourList
    appointments.value = appointmentList
    placeForm.value = {
      name: place.name,
      description: place.description || '',
      address: place.address,
      capacity: place.capacity,
      active: place.active,
    }
    error.value = ''
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

async function createPlace(): Promise<void> {
  busy.value = true
  try {
    const place = await request<Place>('/owner/places', {
      method: 'POST',
      body: JSON.stringify({ ...placeForm.value, description: placeForm.value.description || null }),
    })
    places.value.push(place)
    selectedPlaceId.value = place.id
    setMessage('Mekânın oluşturuldu. Çalışma saatlerini ve hizmetlerini ekleyebilirsin.')
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

async function savePlace(): Promise<void> {
  if (!selectedPlace.value) return
  busy.value = true
  try {
    const saved = await request<Place>(`/owner/places/${selectedPlace.value.id}`, {
      method: 'PUT',
      body: JSON.stringify({ ...placeForm.value, description: placeForm.value.description || null }),
    })
    places.value = places.value.map((place) => place.id === saved.id ? saved : place)
    setMessage('Mekân bilgileri güncellendi.')
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

function editOffering(offering: Offering): void {
  offerForm.value = {
    id: offering.id,
    name: offering.name,
    description: offering.description || '',
    price: offering.price,
    durationMinutes: offering.durationMinutes || 30,
    kind: offering.kind,
    active: offering.active,
  }
}

function resetOffering(): void {
  offerForm.value = { id: '', name: '', description: '', price: 0, durationMinutes: 30, kind: 'SERVICE', active: true }
}

async function saveOffering(): Promise<void> {
  if (!selectedPlace.value) return
  busy.value = true
  const payload = {
    ...offerForm.value,
    description: offerForm.value.description || null,
    durationMinutes: offerForm.value.kind === 'SERVICE' ? offerForm.value.durationMinutes : null,
  }
  try {
    if (offerForm.value.id) {
      await request(`/owner/places/${selectedPlace.value.id}/offerings/${offerForm.value.id}`, {
        method: 'PUT', body: JSON.stringify(payload),
      })
      setMessage('Hizmet güncellendi.')
    } else {
      await request(`/owner/places/${selectedPlace.value.id}/offerings`, {
        method: 'POST', body: JSON.stringify(payload),
      })
      setMessage('Hizmet eklendi.')
    }
    resetOffering()
    await loadPlaceData()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

async function addHour(): Promise<void> {
  if (!selectedPlace.value) return
  busy.value = true
  try {
    await request(`/owner/places/${selectedPlace.value.id}/business-hours`, {
      method: 'POST', body: JSON.stringify(hourForm.value),
    })
    setMessage('Çalışma saati eklendi.')
    await loadPlaceData()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

async function deleteHour(hour: BusinessHour): Promise<void> {
  if (!selectedPlace.value) return
  const dayName = days.find((day) => day.value === hour.dayOfWeek)?.label || hour.dayOfWeek
  if (!window.confirm(`${dayName} ${hour.opensAt.slice(0, 5)}–${hour.closesAt.slice(0, 5)} çalışma saatini silmek istiyor musun?`)) return
  busy.value = true
  try {
    await request(`/owner/places/${selectedPlace.value.id}/business-hours/${hour.id}`, { method: 'DELETE' })
    setMessage('Çalışma saati silindi.')
    await loadPlaceData()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

watch([selectedPlaceId, date, calendarView], () => { void loadPlaceData() })
onMounted(loadPlaces)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="section owner-dashboard">
      <div class="section-heading">
        <div><span class="eyebrow">İŞLETME PANELİ</span><h1>Günlük çalışma alanın</h1></div>
        <label v-if="places.length" class="owner-select">Mekân
          <select v-model="selectedPlaceId"><option v-for="place in places" :key="place.id" :value="place.id">{{ place.name }}</option></select>
        </label>
      </div>
      <p v-if="error" class="booking-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="owner-notice" role="status">{{ notice }}</p>
      <p v-if="loading" class="empty" role="status">İşletme bilgileri yükleniyor…</p>

      <section v-if="!loading && !places.length" class="owner-card">
        <span class="eyebrow">1 / 3 · MEKÂNINI KUR</span>
        <h2>Önce işletmeni tanıtalım.</h2>
        <p>Sonraki adımda çalışma saatlerini ve hizmetlerini tanımlayabilirsin.</p>
        <form class="owner-form-grid" @submit.prevent="createPlace">
          <label>Mekân adı<input v-model="placeForm.name" required maxlength="120"></label>
          <label>Adres<input v-model="placeForm.address" required maxlength="300"></label>
          <label>Açıklama<input v-model="placeForm.description" maxlength="1000"></label>
          <label>Aynı anda hizmet kapasitesi<input v-model.number="placeForm.capacity" type="number" min="1" max="10000" required></label>
          <button class="button primary" :disabled="busy">{{ busy ? 'Kaydediliyor…' : 'Mekânı oluştur' }}</button>
        </form>
      </section>

      <template v-else-if="selectedPlace">
        <nav class="owner-card" aria-label="İşletme kurulum adımları">
          <p>Kurulum: <a href="#owner-settings">1. Mekân</a> → <a href="#owner-hours">2. Çalışma saatleri</a> → <a href="#owner-services">3. Hizmetler</a></p>
          <p v-if="!hours.length">Randevu alabilmek için çalışma saatlerini ekle.</p>
          <p v-else-if="!offerings.some(item => item.active && item.kind === 'SERVICE')">Çalışma saatlerin hazır. Şimdi randevu alınabilir bir hizmet ekle.</p>
        </nav>
        <section id="owner-settings" class="owner-card">
          <div class="owner-section-heading"><div><span class="eyebrow">MEKÂN AYARLARI</span><h2>{{ selectedPlace.name }}</h2></div><span class="status" :class="selectedPlace.active ? '' : 'cancelled'">{{ selectedPlace.active ? 'Yayında' : 'Gizli' }}</span></div>
          <form class="owner-form-grid" @submit.prevent="savePlace">
            <label>Mekân adı<input v-model="placeForm.name" required maxlength="120"></label>
            <label>Adres<input v-model="placeForm.address" required maxlength="300"></label>
            <label>Açıklama<input v-model="placeForm.description" maxlength="1000"></label>
            <label>Kapasite<input v-model.number="placeForm.capacity" type="number" min="1" max="10000" required></label>
            <label class="owner-checkbox"><input v-model="placeForm.active" type="checkbox">Mekânı müşterilere göster</label>
            <button class="button primary" :disabled="busy">Mekân bilgilerini kaydet</button>
          </form>
        </section>

        <section class="owner-card">
          <div id="owner-services" class="owner-section-heading"><div><span class="eyebrow">HİZMETLER</span><h2>Hizmetlerini yönet</h2></div></div>
          <div class="owner-list">
            <article v-for="offering in offerings" :key="offering.id" class="owner-list-row">
              <div><b>{{ offering.name }}</b><small>{{ offering.kind === 'SERVICE' ? `${offering.durationMinutes} dk` : 'Ürün' }} · {{ offering.price.toFixed(2) }} ₺ · {{ offering.active ? 'Yayında' : 'Gizli' }}</small></div>
              <button class="text-button" @click="editOffering(offering)">Düzenle</button>
            </article>
            <p v-if="!offerings.length" class="empty">Henüz hizmet eklenmemiş.</p>
          </div>
          <form class="owner-form-grid owner-editor" @submit.prevent="saveOffering">
            <h3>{{ isEditingOffer ? 'Hizmeti düzenle' : 'Yeni hizmet ekle' }}</h3>
            <label>Ad<input v-model="offerForm.name" required maxlength="120"></label>
            <label>Fiyat (₺)<input v-model.number="offerForm.price" type="number" min="0" step="0.01" required></label>
            <label>Tür<select v-model="offerForm.kind"><option value="SERVICE">Hizmet</option><option value="PRODUCT">Ürün</option></select></label>
            <label v-if="offerForm.kind === 'SERVICE'">Süre (dakika)<input v-model.number="offerForm.durationMinutes" type="number" min="1" max="1440" required></label>
            <label>Açıklama<input v-model="offerForm.description" maxlength="1000"></label>
            <label v-if="isEditingOffer" class="owner-checkbox"><input v-model="offerForm.active" type="checkbox">Müşterilere açık</label>
            <div class="owner-actions"><button class="button primary" :disabled="busy">{{ busy ? 'Kaydediliyor…' : isEditingOffer ? 'Değişiklikleri kaydet' : 'Hizmet ekle' }}</button><button v-if="isEditingOffer" class="text-button" type="button" @click="resetOffering">Vazgeç</button></div>
          </form>
        </section>

        <section class="owner-card">
          <div id="owner-hours" class="owner-section-heading"><div><span class="eyebrow">HAFTALIK TAKVİM</span><h2>Çalışma saatleri</h2></div></div>
          <div class="owner-list">
            <article v-for="hour in hours" :key="hour.id" class="owner-list-row">
              <div><b>{{ days.find((day) => day.value === hour.dayOfWeek)?.label || hour.dayOfWeek }}</b><small>{{ hour.opensAt.slice(0, 5) }} – {{ hour.closesAt.slice(0, 5) }}</small></div>
              <button class="text-button cancel" :disabled="busy" @click="deleteHour(hour)">Sil</button>
            </article>
            <p v-if="!hours.length" class="empty">Henüz çalışma saati eklenmemiş. Saat eklenene kadar randevu alınamaz.</p>
          </div>
          <form class="owner-form-grid owner-editor" @submit.prevent="addHour">
            <h3>Çalışma saati ekle</h3>
            <label>Gün<select v-model="hourForm.dayOfWeek"><option v-for="day in days" :key="day.value" :value="day.value">{{ day.label }}</option></select></label>
            <label>Açılış<input v-model="hourForm.opensAt" type="time" required></label>
            <label>Kapanış<input v-model="hourForm.closesAt" type="time" required></label>
            <button class="button primary" :disabled="busy">Saat ekle</button>
          </form>
        </section>

        <CalendarExceptions :key="selectedPlace.id" :place-id="selectedPlace.id" />

        <section class="owner-card">
          <div class="owner-section-heading">
            <div><span class="eyebrow">{{ calendarView === 'week' ? 'HAFTALIK TAKVİM' : 'GÜNLÜK TAKVİM' }}</span><h2>{{ selectedPlace.name }} randevuları</h2></div>
            <label class="owner-select">Görünüm<select v-model="calendarView"><option value="day">Günlük</option><option value="week">Haftalık</option></select></label>
            <label class="owner-select">Tarih<input v-model="date" type="date"></label>
          </div>
          <div class="owner-list">
            <article v-for="appointment in appointments" :key="appointment.id" class="owner-list-row">
              <div><b>{{ new Date(appointment.startsAt).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) }} · {{ appointment.customerName }}</b><small>{{ appointment.offeringName }} · {{ new Date(appointment.startsAt).toLocaleDateString('tr-TR') }}</small></div>
              <span class="status" :class="appointment.status.toLowerCase()">{{ appointment.status === 'CONFIRMED' ? 'Onaylandı' : 'İptal edildi' }}</span>
            </article>
            <p v-if="!appointments.length" class="empty">Bu {{ calendarView === 'week' ? 'haftada' : 'tarihte' }} randevu bulunmuyor.</p>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

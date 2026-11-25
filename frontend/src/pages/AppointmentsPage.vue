<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import {
  appointmentStatusLabels,
  cancelAppointment as cancelRequest,
  getPlace,
  listAppointments,
  listSlots,
  rescheduleAppointment,
  type Appointment,
  type Place,
} from '../lib/bookings'
import { businessDate, formatDateTime, formatDay, formatMonth, timeFormatter } from '../lib/businessTime'
import { ApiError } from '../lib/api'

const appointments = ref<Appointment[]>([])
const loading = ref(true)
const error = ref('')
const busyId = ref('')
const editingId = ref('')
const editingPlace = ref<Place | null>(null)
const selectedDate = ref('')
const firstDate = ref('')
const placeTimeZone = ref('Europe/Istanbul')
const availableSlots = ref<string[]>([])
const selectedSlot = ref('')
const editError = ref('')
const loadingSlots = ref(false)
const filter = ref<'upcoming' | 'past' | 'cancelled'>('upcoming')
const now = ref(Date.now())
const clockTimer = window.setInterval(() => {
  now.value = Date.now()
}, 15_000)

const visibleAppointments = computed(() => appointments.value.filter((item) => {
  if (filter.value === 'cancelled') return item.status === 'CANCELLED'
  if (item.status === 'CANCELLED') return false
  if (item.status !== 'CONFIRMED') return filter.value === 'past'
  return filter.value === 'upcoming'
    ? new Date(item.startsAt).getTime() > now.value
    : new Date(item.startsAt).getTime() <= now.value
}))

function zoneOf(appointment: Appointment): string {
  return appointment.timeZone || 'Europe/Istanbul'
}

async function loadAppointments(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    appointments.value = await listAppointments()
    now.value = Date.now()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

async function cancelAppointment(appointment: Appointment): Promise<void> {
  if (busyId.value) return
  const date = formatDateTime(appointment.startsAt, zoneOf(appointment))
  if (!window.confirm(`${appointment.placeName} · ${appointment.offeringName}\n${date}\n\nBu randevuyu iptal etmek istiyor musun?`)) return

  busyId.value = appointment.id
  error.value = ''
  try {
    await cancelRequest(appointment.id)
    await loadAppointments()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busyId.value = ''
  }
}

async function startReschedule(appointment: Appointment): Promise<void> {
  if (busyId.value) return
  editingId.value = appointment.id
  editingPlace.value = null
  availableSlots.value = []
  selectedSlot.value = ''
  editError.value = ''
  loadingSlots.value = true
  try {
    const place = await getPlace(appointment.placeId)
    if (editingId.value !== appointment.id) return
    editingPlace.value = place
    placeTimeZone.value = place.timeZone
    selectedDate.value = businessDate(place.timeZone)
    firstDate.value = selectedDate.value
    await loadRescheduleSlots(appointment, place)
  } catch (cause) {
    if (editingId.value !== appointment.id) return
    editError.value = (cause as Error).message
    loadingSlots.value = false
  }
}

async function loadRescheduleSlots(appointment: Appointment, knownPlace?: Place): Promise<void> {
  loadingSlots.value = true
  editError.value = ''
  availableSlots.value = []
  selectedSlot.value = ''
  try {
    if (selectedDate.value < firstDate.value) {
      editError.value = 'Gelecekteki bir tarih seç.'
      return
    }
    const place = knownPlace
      || (editingPlace.value?.id === appointment.placeId ? editingPlace.value : await getPlace(appointment.placeId))
    if (editingId.value !== appointment.id) return
    editingPlace.value = place
    const slots = await listSlots(place, appointment.offeringId, selectedDate.value)
    if (editingId.value !== appointment.id) return
    availableSlots.value = slots
  } catch (cause) {
    if (editingId.value !== appointment.id) return
    editError.value = (cause as Error).message
  } finally {
    if (editingId.value === appointment.id) {
      loadingSlots.value = false
    }
  }
}

async function confirmReschedule(appointment: Appointment): Promise<void> {
  if (busyId.value || !selectedSlot.value) return
  busyId.value = appointment.id
  editError.value = ''
  try {
    await rescheduleAppointment(appointment.id, selectedSlot.value)
    editingId.value = ''
    editingPlace.value = null
    await loadAppointments()
  } catch (cause) {
    await loadRescheduleSlots(appointment)
    editError.value = cause instanceof ApiError && cause.status === 409
      ? 'Bu saat artık uygun değil. Yeni bir saat seç.' : (cause as Error).message
  } finally {
    busyId.value = ''
  }
}

onUnmounted(() => window.clearInterval(clockTimer))
onMounted(loadAppointments)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main id="appointments" class="section appointments-page">
      <div class="section-heading">
        <div><span class="eyebrow">TAKVİMİN</span><h1>Randevularım</h1></div>
        <RouterLink to="/" class="button primary">Yeni randevu al</RouterLink>
      </div>
      <div class="appointment-filters" role="tablist" aria-label="Randevu türü">
        <button v-for="item in [{ id: 'upcoming', label: 'Yaklaşan' }, { id: 'past', label: 'Geçmiş' }, { id: 'cancelled', label: 'İptal edilen' }]" :key="item.id" class="filter-button" :class="{ active: filter === item.id }" role="tab" :aria-selected="filter === item.id" @click="filter = item.id as typeof filter">
          {{ item.label }}
        </button>
      </div>
      <p v-if="loading" class="empty" role="status">Randevular yükleniyor…</p>
      <p v-else-if="error" class="booking-error" role="alert">{{ error }} <button class="text-button" @click="loadAppointments">Yeniden dene</button></p>
      <div v-else-if="visibleAppointments.length" class="appointment-list">
        <article v-for="item in visibleAppointments" :key="item.id" class="appointment appointment-card">
          <div class="appointment-date">
            <b>{{ formatDay(item.startsAt, zoneOf(item)) }}</b>
            <small>{{ formatMonth(item.startsAt, zoneOf(item)) }}</small>
          </div>
          <div class="appointment-info">
            <b>{{ item.offeringName }}</b>
            <small>{{ item.placeName }} · {{ formatDateTime(item.startsAt, zoneOf(item)) }}</small>
          </div>
          <span class="status" :class="item.status.toLowerCase()">{{ appointmentStatusLabels[item.status] }}</span>
          <button v-if="filter === 'upcoming'" class="text-button cancel" :disabled="busyId === item.id" @click="cancelAppointment(item)">
            {{ busyId === item.id ? 'İptal ediliyor…' : 'İptal et' }}
          </button>
          <button v-if="filter === 'upcoming'" class="text-button" :disabled="!!busyId" @click="startReschedule(item)">Saat değiştir</button>
          <div v-if="filter === 'upcoming' && editingId === item.id" class="reschedule-form">
            <label>Yeni tarih <input v-model="selectedDate" type="date" :min="firstDate" :disabled="loadingSlots" @change="loadRescheduleSlots(item)" /></label>
            <p v-if="loadingSlots" class="empty" role="status">Saatler yükleniyor…</p>
            <label v-else>Yeni saat
              <select v-model="selectedSlot" :disabled="!availableSlots.length">
                <option value="">Saat seç</option>
                <option v-for="slot in availableSlots" :key="slot" :value="slot">{{ timeFormatter(slot, placeTimeZone).format(new Date(slot)) }}</option>
              </select>
            </label>
            <p v-if="!loadingSlots && !availableSlots.length" class="empty">Bu tarihte uygun saat yok.</p>
            <p v-if="editError" class="booking-error" role="alert">{{ editError }}</p>
            <div class="reschedule-actions">
              <button type="button" class="button primary" :disabled="!selectedSlot || !!busyId" @click="confirmReschedule(item)">Yeni saati onayla</button>
              <button type="button" class="text-button" @click="editingId = ''">Vazgeç</button>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="booking-empty">
        <b>{{ filter === 'upcoming' ? 'Yaklaşan randevun yok' : filter === 'past' ? 'Geçmiş randevun yok' : 'İptal edilen randevun yok' }}</b>
        <p>{{ filter === 'upcoming' ? 'Mekânlara göz atıp sana uygun bir saat seçebilirsin.' : 'Bu bölümde gösterilecek randevu bulunmuyor.' }}</p>
        <RouterLink v-if="filter === 'upcoming'" class="text-button" to="/">Mekânları keşfet →</RouterLink>
      </div>
    </main>
  </div>
</template>

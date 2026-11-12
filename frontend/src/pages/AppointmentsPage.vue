<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { token } from '../composables/auth'
import { request } from '../lib/api'

type Appointment = {
  id: string
  placeId: string
  placeName: string
  offeringName: string
  startsAt: string
  endsAt: string
  status: 'CONFIRMED' | 'CANCELLED' | string
}

const appointments = ref<Appointment[]>([])
const loading = ref(true)
const error = ref('')
const busyId = ref('')
const filter = ref<'upcoming' | 'past' | 'cancelled'>('upcoming')
const now = Date.now()
const visibleAppointments = computed(() => appointments.value.filter((item) => {
  if (filter.value === 'cancelled') return item.status === 'CANCELLED'
  if (item.status !== 'CONFIRMED') return false
  return filter.value === 'upcoming'
    ? new Date(item.startsAt).getTime() >= now
    : new Date(item.startsAt).getTime() < now
}))

async function loadAppointments(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    appointments.value = await request<Appointment[]>('/appointments/me')
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

async function cancelAppointment(appointment: Appointment): Promise<void> {
  const date = new Date(appointment.startsAt).toLocaleString('tr-TR', { dateStyle: 'long', timeStyle: 'short' })
  if (!window.confirm(`${appointment.placeName} · ${appointment.offeringName}\n${date}\n\nBu randevuyu iptal etmek istiyor musun?`)) return

  busyId.value = appointment.id
  error.value = ''
  try {
    await request(`/appointments/${appointment.id}`, { method: 'DELETE' })
    await loadAppointments()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busyId.value = ''
  }
}

onMounted(loadAppointments)
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/"><span class="brand-mark">S</span>slotly</RouterLink>
      <nav><RouterLink to="/">Mekânlar</RouterLink><a href="#appointments">Randevularım</a></nav>
      <RouterLink v-if="!token" class="button quiet" to="/login">Giriş yap</RouterLink>
    </header>
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
            <b>{{ new Date(item.startsAt).toLocaleDateString('tr-TR', { day: '2-digit' }) }}</b>
            <small>{{ new Date(item.startsAt).toLocaleDateString('tr-TR', { month: 'short' }) }}</small>
          </div>
          <div class="appointment-info">
            <b>{{ item.offeringName }}</b>
            <small>{{ item.placeName }} · {{ new Date(item.startsAt).toLocaleString('tr-TR', { dateStyle: 'long', timeStyle: 'short' }) }}</small>
          </div>
          <span class="status" :class="item.status.toLowerCase()">{{ item.status === 'CONFIRMED' ? 'Onaylandı' : 'İptal edildi' }}</span>
          <button v-if="filter === 'upcoming'" class="text-button cancel" :disabled="busyId === item.id" @click="cancelAppointment(item)">
            {{ busyId === item.id ? 'İptal ediliyor…' : 'İptal et' }}
          </button>
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

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { request } from '../lib/api'

type Appointment = {
  id: string
  placeName: string
  offeringName: string
  customerUsername: string
  startsAt: string
  endsAt: string
  status: string
}
type WaitlistEntry = {
  id: string
  placeName: string
  offeringName: string
  customerUsername: string
  requestedStartsAt: string
  status: string
  offerExpiresAt: string | null
}

const appointments = ref<Appointment[]>([])
const waitlist = ref<WaitlistEntry[]>([])
const loading = ref(true)
const error = ref('')
const tab = ref<'appointments' | 'waitlist'>('appointments')
const query = ref('')
const now = Date.now()
const upcoming = computed(() => appointments.value.filter((item) => item.status === 'CONFIRMED' && Date.parse(item.startsAt) >= now).length)
const waiting = computed(() => waitlist.value.filter((item) => item.status === 'WAITING').length)
const offers = computed(() => waitlist.value.filter((item) => item.status === 'OFFERED').length)
const visibleAppointments = computed(() => appointments.value.filter((item) => matches(item.placeName, item.customerUsername, item.offeringName)))
const visibleWaitlist = computed(() => waitlist.value.filter((item) => matches(item.placeName, item.customerUsername, item.offeringName)))

function matches(...values: string[]): boolean {
  const term = query.value.trim().toLocaleLowerCase('tr-TR')
  return values.some((value) => value.toLocaleLowerCase('tr-TR').includes(term))
}

function dateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR', { dateStyle: 'medium', timeStyle: 'short' })
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const data = await request<{ appointments: Appointment[]; waitlist: WaitlistEntry[] }>('/admin/overview')
    appointments.value = data.appointments
    waitlist.value = data.waitlist
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="admin-page section">
      <div class="section-heading"><div><span class="eyebrow">SLOTLY YÖNETİMİ</span><h1>Tüm hareketler, tek bakışta.</h1></div><button class="button quiet" @click="load">Yenile ↻</button></div>
      <div class="admin-stats">
        <article><span>Yaklaşan randevular</span><strong>{{ upcoming }}</strong></article>
        <article><span>Sırada bekleyenler</span><strong>{{ waiting }}</strong></article>
        <article><span>Aktif teklifler</span><strong>{{ offers }}</strong></article>
      </div>
      <div class="admin-toolbar">
        <div class="appointment-filters" role="tablist" aria-label="Kayıt türü">
          <button class="filter-button" :class="{ active: tab === 'appointments' }" role="tab" :aria-selected="tab === 'appointments'" @click="tab = 'appointments'">Randevular</button>
          <button class="filter-button" :class="{ active: tab === 'waitlist' }" role="tab" :aria-selected="tab === 'waitlist'" @click="tab = 'waitlist'">Bekleme listesi</button>
        </div>
        <label class="place-search"><span>İşletme, müşteri veya hizmet ara</span><input v-model="query" type="search" placeholder="Kayıtlarda ara"></label>
      </div>
      <p v-if="loading" role="status">Kayıtlar yükleniyor…</p>
      <p v-else-if="error" class="booking-error" role="alert">{{ error }}</p>
      <div v-else class="admin-records">
        <template v-if="tab === 'appointments'">
          <article v-for="item in visibleAppointments" :key="item.id" class="admin-record"><div><b>{{ item.offeringName }}</b><span>{{ item.placeName }} · {{ item.customerUsername }}</span></div><time :datetime="item.startsAt">{{ dateTime(item.startsAt) }}</time><span class="status">{{ item.status }}</span></article>
          <p v-if="!visibleAppointments.length" class="booking-empty">Randevu bulunamadı.</p>
        </template>
        <template v-else>
          <article v-for="item in visibleWaitlist" :key="item.id" class="admin-record"><div><b>{{ item.offeringName }}</b><span>{{ item.placeName }} · {{ item.customerUsername }}</span></div><time :datetime="item.requestedStartsAt">{{ dateTime(item.requestedStartsAt) }}</time><span class="status">{{ item.status }}</span></article>
          <p v-if="!visibleWaitlist.length" class="booking-empty">Bekleme kaydı bulunamadı.</p>
        </template>
      </div>
    </main>
  </div>
</template>

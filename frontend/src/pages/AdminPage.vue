<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { appointmentStatusLabels } from '../lib/bookings'
import { request } from '../lib/api'

const waitlistLabels: Record<string, string> = {
  WAITING: 'Sırada',
  OFFERED: 'Teklif var',
  ACCEPTED: 'Kabul edildi',
  DECLINED: 'Reddedildi',
  EXPIRED: 'Süresi doldu',
  LEFT: 'Ayrıldı',
}

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

type Overview = {
  appointments: Appointment[]
  waitlist: WaitlistEntry[]
  appointmentTotal: number
  waitlistTotal: number
  upcoming: number
  waiting: number
  offers: number
}

const overview = ref<Overview>({
  appointments: [], waitlist: [], appointmentTotal: 0, waitlistTotal: 0, upcoming: 0, waiting: 0, offers: 0,
})
const loading = ref(true)
const error = ref('')
const tab = ref<'appointments' | 'waitlist'>('appointments')
const search = ref('')
const query = ref('')
const pageSize = 20
const appointmentPage = ref(0)
const waitlistPage = ref(0)
const page = computed({
  get: () => tab.value === 'appointments' ? appointmentPage.value : waitlistPage.value,
  set: (value: number) => {
    if (tab.value === 'appointments') appointmentPage.value = value
    else waitlistPage.value = value
  },
})
const total = computed(() => tab.value === 'appointments' ? overview.value.appointmentTotal : overview.value.waitlistTotal)
const pageCount = computed(() => Math.ceil(total.value / pageSize))
let controller: AbortController | undefined

function dateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Istanbul',
  })
}

function appointmentLabel(status: string): string {
  return appointmentStatusLabels[status as keyof typeof appointmentStatusLabels] || status
}

function waitlistLabel(status: string): string {
  return waitlistLabels[status] || status
}

async function load(): Promise<void> {
  controller?.abort()
  controller = new AbortController()
  const { signal } = controller
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({
      appointmentPage: String(appointmentPage.value), waitlistPage: String(waitlistPage.value),
      size: String(pageSize), q: query.value,
    })
    const result = await request<Overview>(`/admin/overview?${params}`, { signal })
    if (signal.aborted) return
    overview.value = result
    appointmentPage.value = Math.min(appointmentPage.value, Math.max(0, Math.ceil(result.appointmentTotal / pageSize) - 1))
    waitlistPage.value = Math.min(waitlistPage.value, Math.max(0, Math.ceil(result.waitlistTotal / pageSize) - 1))
  } catch (cause) {
    if (!signal.aborted) error.value = (cause as Error).message
  } finally {
    if (!signal.aborted) loading.value = false
  }
}

watch(search, (value, _, onCleanup) => {
  const timer = window.setTimeout(() => {
    query.value = value.trim()
    appointmentPage.value = 0
    waitlistPage.value = 0
  }, 250)
  onCleanup(() => window.clearTimeout(timer))
})
watch([appointmentPage, waitlistPage, query], load, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="admin-page section">
      <div class="section-heading"><div><span class="eyebrow">SLOTLY YÖNETİMİ</span><h1>Tüm hareketler, tek bakışta.</h1></div><button class="button quiet" @click="load">Yenile ↻</button></div>
      <div class="admin-stats">
        <article><span>Yaklaşan randevular</span><strong>{{ overview.upcoming }}</strong></article>
        <article><span>Sırada bekleyenler</span><strong>{{ overview.waiting }}</strong></article>
        <article><span>Aktif teklifler</span><strong>{{ overview.offers }}</strong></article>
      </div>
      <div class="admin-toolbar">
        <div class="appointment-filters" role="tablist" aria-label="Kayıt türü">
          <button class="filter-button" :class="{ active: tab === 'appointments' }" role="tab" :aria-selected="tab === 'appointments'" @click="tab = 'appointments'">Randevular</button>
          <button class="filter-button" :class="{ active: tab === 'waitlist' }" role="tab" :aria-selected="tab === 'waitlist'" @click="tab = 'waitlist'">Bekleme listesi</button>
        </div>
        <label class="place-search"><span>İşletme, müşteri veya hizmet ara</span><input v-model="search" type="search" placeholder="Kayıtlarda ara"></label>
      </div>
      <p v-if="loading" role="status">Kayıtlar yükleniyor…</p>
      <p v-else-if="error" class="booking-error" role="alert">{{ error }}</p>
      <div v-else class="admin-records">
        <template v-if="tab === 'appointments'">
          <article v-for="item in overview.appointments" :key="item.id" class="admin-record"><div><b>{{ item.offeringName }}</b><span>{{ item.placeName }} · {{ item.customerUsername }}</span></div><time :datetime="item.startsAt">{{ dateTime(item.startsAt) }}</time><span class="status" :class="item.status.toLowerCase()">{{ appointmentLabel(item.status) }}</span></article>
          <p v-if="!overview.appointments.length" class="booking-empty">Randevu bulunamadı.</p>
        </template>
        <template v-else>
          <article v-for="item in overview.waitlist" :key="item.id" class="admin-record"><div><b>{{ item.offeringName }}</b><span>{{ item.placeName }} · {{ item.customerUsername }}</span></div><time :datetime="item.requestedStartsAt">{{ dateTime(item.requestedStartsAt) }}</time><span class="status" :class="{ 'waitlist-offer-status': item.status === 'OFFERED' }">{{ waitlistLabel(item.status) }}</span></article>
          <p v-if="!overview.waitlist.length" class="booking-empty">Bekleme kaydı bulunamadı.</p>
        </template>
        <nav v-if="pageCount > 1" class="owner-actions" aria-label="Kayıt sayfaları">
          <button class="text-button" :disabled="page === 0" @click="page--">Önceki</button>
          <span>Sayfa {{ page + 1 }} / {{ pageCount }}</span>
          <button class="text-button" :disabled="page + 1 >= pageCount" @click="page++">Sonraki</button>
        </nav>
      </div>
    </main>
  </div>
</template>

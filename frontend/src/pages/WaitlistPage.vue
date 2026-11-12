<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { clearAuth, role, token } from '../composables/auth'
import { API_BASE_URL, request } from '../lib/api'

type WaitlistEntry = {
  id: string
  placeId: string
  placeName: string
  offeringId: string
  offeringName: string
  startsAt: string
  endsAt: string
  status: 'WAITING' | 'OFFERED' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'LEFT' | string
  offerExpiresAt: string | null
}

const entries = ref<WaitlistEntry[]>([])
const loading = ref(true)
const busyId = ref('')
const error = ref('')
const notice = ref('')
const now = ref(Date.now())
const readIds = ref<string[]>(readStoredIds())
const activeOffers = computed(() => entries.value.filter((entry) => entry.status === 'OFFERED'))
const unreadCount = computed(() => activeOffers.value.filter((entry) => !readIds.value.includes(entry.id)).length)
const orderedEntries = computed(() => [...entries.value].sort((a, b) => {
  const priority = (entry: WaitlistEntry) => entry.status === 'OFFERED' ? 0 : entry.status === 'WAITING' ? 1 : 2
  return priority(a) - priority(b) || new Date(b.startsAt).getTime() - new Date(a.startsAt).getTime()
}))

let refreshTimer: number | undefined
let clockTimer: number | undefined
let eventController: AbortController | undefined

function readStoredIds(): string[] {
  try {
    const stored = localStorage.getItem('slotly-read-waitlist-offers')
    return stored ? JSON.parse(stored) as string[] : []
  } catch {
    return []
  }
}

function saveReadIds(ids: string[]): void {
  readIds.value = ids
  localStorage.setItem('slotly-read-waitlist-offers', JSON.stringify(ids))
}

async function loadEntries(): Promise<void> {
  try {
    const previousOfferIds = new Set(activeOffers.value.map((entry) => entry.id))
    entries.value = await request<WaitlistEntry[]>('/waitlist/me')
    const newOffer = activeOffers.value.some((entry) => !previousOfferIds.has(entry.id))
    if (newOffer) notice.value = 'Yeni bir randevu teklifi geldi. Teklifin son saatini kontrol et.'
    error.value = ''
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

function markOffersRead(): void {
  saveReadIds([...new Set([...readIds.value, ...activeOffers.value.map((entry) => entry.id)])])
}

function timeRemaining(entry: WaitlistEntry): string {
  if (!entry.offerExpiresAt) return ''
  const seconds = Math.max(0, Math.ceil((new Date(entry.offerExpiresAt).getTime() - now.value) / 1000))
  const minutes = Math.floor(seconds / 60)
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

function isExpired(entry: WaitlistEntry): boolean {
  return entry.status === 'OFFERED' && (!entry.offerExpiresAt || new Date(entry.offerExpiresAt).getTime() <= now.value)
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    WAITING: 'Sırada', OFFERED: 'Teklif var', ACCEPTED: 'Kabul edildi',
    DECLINED: 'Reddedildi', EXPIRED: 'Süresi doldu', LEFT: 'Ayrıldın',
  }
  return labels[status] || status
}

async function act(entry: WaitlistEntry, action: 'accept' | 'decline' | 'leave'): Promise<void> {
  if (action === 'accept' && isExpired(entry)) {
    await loadEntries()
    error.value = 'Teklifin süresi dolmuş. Durumu sunucudan yeniledim.'
    return
  }
  busyId.value = entry.id
  error.value = ''
  try {
    await request(action === 'leave' ? `/waitlist/${entry.id}` : `/waitlist/${entry.id}/${action}`, {
      method: action === 'leave' ? 'DELETE' : 'POST',
    })
    notice.value = action === 'accept' ? 'Teklif kabul edildi, randevun oluşturuldu.' : action === 'decline' ? 'Teklif reddedildi.' : 'Bekleme listesinden ayrıldın.'
    await loadEntries()
  } catch (cause) {
    error.value = (cause as Error).message
    await loadEntries()
  } finally {
    busyId.value = ''
  }
}

function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(resolve, milliseconds)
    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      resolve()
    }, { once: true })
  })
}

async function connectEvents(signal: AbortSignal): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/waitlist/events`, {
    headers: { Authorization: `Bearer ${token.value}`, Accept: 'text/event-stream' },
    signal,
  })
  if (response.status === 401) {
    clearAuth()
    window.dispatchEvent(new CustomEvent('slotly:unauthorized'))
    throw new Error('Oturum süresi doldu. Yeniden giriş yap.')
  }
  if (!response.ok || !response.body) throw new Error(`Bildirim bağlantısı başarısız (${response.status})`)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let isOfferEvent = false
  while (!signal.aborted) {
    const { value, done } = await reader.read()
    if (done) throw new Error('Bildirim bağlantısı kapandı')
    buffer += decoder.decode(value, { stream: true })
    let boundary = buffer.indexOf('\n')
    while (boundary >= 0) {
      const line = buffer.slice(0, boundary).replace(/\r$/, '')
      buffer = buffer.slice(boundary + 1)
      if (line.startsWith('event:')) isOfferEvent = line.slice(6).trim() === 'offer'
      if (line === '' && isOfferEvent) {
        isOfferEvent = false
        void loadEntries()
      }
      boundary = buffer.indexOf('\n')
    }
  }
}

async function maintainEventStream(signal: AbortSignal): Promise<void> {
  let retryDelay = 1000
  while (!signal.aborted) {
    try {
      await connectEvents(signal)
      retryDelay = 1000
    } catch {
      if (signal.aborted) return
      await wait(retryDelay, signal)
      retryDelay = Math.min(retryDelay * 2, 30_000)
    }
  }
}

onMounted(() => {
  void loadEntries()
  refreshTimer = window.setInterval(loadEntries, 30_000)
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
  if (token.value && role.value === 'CUSTOMER') {
    eventController = new AbortController()
    void maintainEventStream(eventController.signal)
  }
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  if (clockTimer) window.clearInterval(clockTimer)
  eventController?.abort()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/"><span class="brand-mark">S</span>slotly</RouterLink>
      <nav><RouterLink to="/">Mekânlar</RouterLink><RouterLink to="/appointments">Randevularım</RouterLink><a href="#waitlist">Bekleme listesi</a></nav>
    </header>
    <main id="waitlist" class="section waitlist-page">
      <div class="section-heading">
        <div><span class="eyebrow">BEKLEME LİSTESİ</span><h1>Tekliflerin</h1></div>
        <p>Teklif geldiğinde 30 dakika içinde kabul edebilirsin.</p>
      </div>
      <p v-if="unreadCount" class="waitlist-notice" role="status">
        {{ unreadCount }} okunmamış teklifin var.
        <button class="text-button" @click="markOffersRead">Okundu olarak işaretle</button>
      </p>
      <p v-if="notice" class="owner-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="booking-error" role="alert">{{ error }} <button class="text-button" @click="loadEntries">Yeniden dene</button></p>
      <p v-if="loading" class="empty" role="status">Bekleme listen yükleniyor…</p>
      <div v-else-if="orderedEntries.length" class="appointment-list">
        <article v-for="entry in orderedEntries" :key="entry.id" class="appointment waitlist-entry" :class="{ 'offer-active': entry.status === 'OFFERED' }">
          <div class="appointment-date"><b>{{ new Date(entry.startsAt).toLocaleDateString('tr-TR', { day: '2-digit' }) }}</b><small>{{ new Date(entry.startsAt).toLocaleDateString('tr-TR', { month: 'short' }) }}</small></div>
          <div class="appointment-info">
            <b>{{ entry.offeringName }}</b>
            <small>{{ entry.placeName }} · {{ new Date(entry.startsAt).toLocaleString('tr-TR', { dateStyle: 'long', timeStyle: 'short' }) }}</small>
            <small v-if="entry.status === 'OFFERED' && entry.offerExpiresAt" class="offer-countdown" :class="{ expired: isExpired(entry) }">
              {{ isExpired(entry) ? 'Teklif doğrulanıyor…' : `Kalan süre ${timeRemaining(entry)}` }} · Son: {{ new Date(entry.offerExpiresAt).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) }}
            </small>
          </div>
          <span class="status" :class="{ 'waitlist-offer-status': entry.status === 'OFFERED' }">{{ statusLabel(entry.status) }}</span>
          <div v-if="entry.status === 'OFFERED'" class="waitlist-actions">
            <button class="button primary" :disabled="busyId === entry.id || isExpired(entry)" @click="act(entry, 'accept')">{{ busyId === entry.id ? 'Gönderiliyor…' : 'Kabul et' }}</button>
            <button class="text-button cancel" :disabled="busyId === entry.id" @click="act(entry, 'decline')">Reddet</button>
          </div>
          <button v-else-if="entry.status === 'WAITING'" class="text-button cancel" :disabled="busyId === entry.id" @click="act(entry, 'leave')">Listeden ayrıl</button>
          <RouterLink v-else-if="entry.status === 'ACCEPTED'" class="text-button" to="/appointments">Randevuyu gör →</RouterLink>
        </article>
      </div>
      <div v-else class="booking-empty"><b>Bekleme listende kayıt yok</b><p>İstediğin saat doluysa mekân sayfasından sıraya katılabilirsin.</p><RouterLink class="text-button" to="/">Mekânları keşfet →</RouterLink></div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import {
  deadline, entries, feedLoading as loading, feedError, markOffersRead, now, statusAt,
  refreshEntries as loadEntries, unreadOffers as unreadCount, type WaitlistEntry,
} from '../composables/waitlist'
import { formatDateTime, formatDay, formatMonth, timeFormatter } from '../lib/businessTime'
import { request } from '../lib/api'

function zoneOf(entry: WaitlistEntry): string {
  return entry.timeZone || 'Europe/Istanbul'
}

function deadlineTime(entry: WaitlistEntry): string {
  const timestamp = deadline(entry)
  return timeFormatter(new Date(timestamp).toISOString(), zoneOf(entry)).format(timestamp)
}

const busyId = ref('')
const actionError = ref('')
const error = computed(() => actionError.value || feedError.value)
const notice = ref('')
const orderedEntries = computed(() => {
  const current = entries.value.map(entry => ({ ...entry, status: statusAt(entry, now.value) }))
  const priority = (entry: WaitlistEntry) => entry.status === 'OFFERED' ? 0 : entry.status === 'WAITING' ? 1 : 2
  return current.sort((a, b) => priority(a) - priority(b) || Date.parse(b.startsAt) - Date.parse(a.startsAt))
})

function timeRemaining(entry: WaitlistEntry): string {
  if (!entry.offerExpiresAt) return ''
  const seconds = Math.max(0, Math.ceil((deadline(entry) - now.value) / 1000))
  return String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0')
}

function statusLabel(status: WaitlistEntry['status']): string {
  const labels: Record<WaitlistEntry['status'], string> = {
    WAITING: 'Sırada',
    OFFERED: 'Teklif var',
    ACCEPTED: 'Kabul edildi',
    DECLINED: 'Reddedildi',
    EXPIRED: 'Süresi doldu',
    LEFT: 'Ayrıldın',
  }
  return labels[status] || 'Durum güncelleniyor'
}

async function act(entry: WaitlistEntry, action: 'accept' | 'decline' | 'leave'): Promise<void> {
  if (busyId.value) return
  if (statusAt(entry, Date.now()) === 'EXPIRED') {
    await loadEntries()
    actionError.value = 'Teklifin süresi dolmuş. Durumu sunucudan yeniledim.'
    return
  }
  busyId.value = entry.id
  actionError.value = ''
  try {
    const path = '/waitlist/' + entry.id + (action === 'leave' ? '' : '/' + action)
    await request(path, { method: action === 'leave' ? 'DELETE' : 'POST' })
    notice.value = action === 'accept' ? 'Teklif kabul edildi, randevun oluşturuldu.'
      : action === 'decline' ? 'Teklif reddedildi.' : 'Bekleme listesinden ayrıldın.'
    await loadEntries()
  } catch (cause) {
    await loadEntries()
    actionError.value = (cause as Error).message
  } finally {
    busyId.value = ''
  }
}

onMounted(loadEntries)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main id="waitlist" class="section waitlist-page">
      <div class="section-heading">
        <div><span class="eyebrow">BEKLEME LİSTESİ</span><h1>Tekliflerin</h1></div>
        <p>Teklifini en geç 30 dakika içinde ve randevu başlamadan kabul edebilirsin.</p>
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
          <div class="appointment-date"><b>{{ formatDay(entry.startsAt, zoneOf(entry)) }}</b><small>{{ formatMonth(entry.startsAt, zoneOf(entry)) }}</small></div>
          <div class="appointment-info">
            <b>{{ entry.offeringName }}</b>
            <small>{{ entry.placeName }} · {{ formatDateTime(entry.startsAt, zoneOf(entry)) }}</small>
            <small v-if="entry.status === 'OFFERED'" class="offer-countdown">
              Kalan süre {{ timeRemaining(entry) }} · Son: {{ deadlineTime(entry) }}
            </small>
          </div>
          <span class="status" :class="{ 'waitlist-offer-status': entry.status === 'OFFERED' }">{{ statusLabel(entry.status) }}</span>
          <div v-if="entry.status === 'OFFERED'" class="waitlist-actions">
            <button class="button primary" :disabled="Boolean(busyId)" @click="act(entry, 'accept')">{{ busyId === entry.id ? 'Gönderiliyor…' : 'Kabul et' }}</button>
            <button class="text-button cancel" :disabled="Boolean(busyId)" @click="act(entry, 'decline')">Reddet</button>
          </div>
          <button v-else-if="entry.status === 'WAITING'" class="text-button cancel" :disabled="Boolean(busyId)" @click="act(entry, 'leave')">Listeden ayrıl</button>
          <RouterLink v-else-if="entry.status === 'ACCEPTED'" class="text-button" to="/appointments">Randevuyu gör →</RouterLink>
        </article>
      </div>
      <div v-else class="booking-empty"><b>Bekleme listende kayıt yok</b><p>İstediğin saat doluysa mekân sayfasından sıraya katılabilirsin.</p><RouterLink class="text-button" to="/">Mekânları keşfet →</RouterLink></div>
    </main>
  </div>
</template>

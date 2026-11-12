<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { request } from '../lib/api'
import { clearAuth, role, token } from '../composables/auth'

type OfferingKind = 'SERVICE' | 'PRODUCT'

type Place = {
  id: string
  name: string
  description: string | null
  address: string
  capacity: number
  active: boolean
}

type Offering = {
  id: string
  placeId: string
  name: string
  description: string | null
  price: number
  durationMinutes: number | null
  kind: OfferingKind
  active: boolean
}

type Appointment = {
  id: string
  placeId: string
  placeName: string
  offeringName: string
  customerName?: string
  startsAt: string
  endsAt: string
  status: string
}

type WaitlistEntry = {
  id: string
  placeId: string
  placeName: string
  offeringId: string
  offeringName: string
  startsAt: string
  endsAt: string
  status: string
  offerExpiresAt: string | null
}

const places = ref<Place[]>([])
const offerings = ref<Offering[]>([])
const appointments = ref<Appointment[]>([])
const waitlistEntries = ref<WaitlistEntry[]>([])
const ownerAppointments = ref<Appointment[]>([])
const selectedPlace = ref<Place | null>(null)
const selectedOffering = ref<Offering | null>(null)
const slots = ref<string[]>([])
const date = ref(getLocalDate())
const message = ref('')
const placeName = ref('')
const placeAddress = ref('')
const placeDescription = ref('')
const placeCapacity = ref(1)
const serviceName = ref('')
const servicePrice = ref(0)
const serviceDuration = ref(30)
const offeringKind = ref<OfferingKind>('SERVICE')
const hourDay = ref(1)
const hourOpen = ref('09:00')
const hourClose = ref('17:00')
const desiredTime = ref('09:00')
const isOwner = computed(() => Boolean(token.value) && role.value === 'OWNER')

let refreshTimer: number | undefined

function getLocalDate(): string {
  const now = new Date()
  const localOffset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - localOffset).toISOString().slice(0, 10)
}

async function loadPlaces(): Promise<void> {
  const path = isOwner.value ? '/owner/places' : '/places'
  places.value = await request<Place[]>(path)
}

function logout(): void {
  clearAuth()
  appointments.value = []
  waitlistEntries.value = []
  ownerAppointments.value = []
  message.value = 'Çıkış yapıldı.'
  void loadPlaces()
}

async function loadOwnerAppointments(): Promise<void> {
  if (!selectedPlace.value) {
    return
  }

  try {
    const dayStart = new Date(`${date.value}T00:00:00`)
    const from = dayStart.toISOString()
    const to = new Date(dayStart.getTime() + 86_400_000).toISOString()
    const query = new URLSearchParams({ placeId: selectedPlace.value.id, from, to })
    ownerAppointments.value = await request<Appointment[]>(`/owner/appointments?${query}`)
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function loadMine(): Promise<void> {
  if (!token.value) {
    return
  }

  try {
    if (role.value === 'OWNER') {
      appointments.value = []
      waitlistEntries.value = []
      await loadOwnerAppointments()
      return
    }

    appointments.value = await request<Appointment[]>('/appointments/me')
    waitlistEntries.value = await request<WaitlistEntry[]>('/waitlist/me')
  } catch {
    // Keep already loaded dashboard data if a refresh request fails.
  }
}

async function choosePlace(place: Place): Promise<void> {
  selectedPlace.value = place
  selectedOffering.value = null
  slots.value = []

  try {
    offerings.value = await request<Offering[]>(`/places/${place.id}/offerings`)
    if (isOwner.value) {
      await loadOwnerAppointments()
    }
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function loadSlots(): Promise<void> {
  if (!selectedPlace.value || !selectedOffering.value) {
    return
  }

  message.value = ''
  try {
    const from = new Date(`${date.value}T00:00:00`).toISOString()
    const to = new Date(new Date(`${date.value}T00:00:00`).getTime() + 86_400_000).toISOString()
    const query = new URLSearchParams({ offeringId: selectedOffering.value.id, from, to })
    slots.value = await request<string[]>(`/places/${selectedPlace.value.id}/availability?${query}`)
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function book(slot: string): Promise<void> {
  if (!selectedPlace.value || !selectedOffering.value) {
    return
  }

  try {
    await request('/appointments', {
      method: 'POST',
      body: JSON.stringify({
        placeId: selectedPlace.value.id,
        offeringId: selectedOffering.value.id,
        startsAt: slot,
      }),
    })
    message.value = 'Randevunuz oluşturuldu.'
    await loadSlots()
    await loadMine()
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function cancel(appointmentId: string): Promise<void> {
  try {
    await request(`/appointments/${appointmentId}`, { method: 'DELETE' })
    message.value = 'Randevu iptal edildi.'
    await loadMine()
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function joinWaitlist(): Promise<void> {
  if (!selectedPlace.value || !selectedOffering.value) {
    return
  }

  try {
    const startsAt = new Date(`${date.value}T${desiredTime.value}:00`).toISOString()
    await request('/waitlist', {
      method: 'POST',
      body: JSON.stringify({
        placeId: selectedPlace.value.id,
        offeringId: selectedOffering.value.id,
        startsAt,
      }),
    })
    message.value = 'Bekleme listesine eklendin. Sıran gelirse 30 dakikalık teklif alacaksın.'
    await loadMine()
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function acceptOffer(entryId: string): Promise<void> {
  try {
    await request(`/waitlist/${entryId}/accept`, { method: 'POST' })
    message.value = 'Teklif kabul edildi ve randevun oluşturuldu.'
    await loadMine()
  } catch (error) {
    message.value = (error as Error).message
    await loadMine()
  }
}

async function declineOffer(entryId: string): Promise<void> {
  try {
    await request(`/waitlist/${entryId}/decline`, { method: 'POST' })
    message.value = 'Teklif reddedildi.'
    await loadMine()
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function leaveWaitlist(entryId: string): Promise<void> {
  try {
    await request(`/waitlist/${entryId}`, { method: 'DELETE' })
    await loadMine()
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function createPlace(): Promise<void> {
  try {
    const createdPlace = await request<Place>('/owner/places', {
      method: 'POST',
      body: JSON.stringify({
        name: placeName.value,
        description: placeDescription.value,
        address: placeAddress.value,
        capacity: placeCapacity.value,
        active: true,
      }),
    })
    message.value = 'Mekân eklendi.'
    placeName.value = ''
    placeAddress.value = ''
    await loadPlaces()
    await choosePlace(createdPlace)
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function createOffering(): Promise<void> {
  if (!selectedPlace.value) {
    return
  }

  try {
    await request(`/owner/places/${selectedPlace.value.id}/offerings`, {
      method: 'POST',
      body: JSON.stringify({
        name: serviceName.value,
        description: null,
        price: servicePrice.value,
        durationMinutes: offeringKind.value === 'SERVICE' ? serviceDuration.value : null,
        kind: offeringKind.value,
        active: true,
      }),
    })
    message.value = offeringKind.value === 'SERVICE' ? 'Hizmet eklendi.' : 'Ürün eklendi.'
    serviceName.value = ''
    await choosePlace(selectedPlace.value)
  } catch (error) {
    message.value = (error as Error).message
  }
}

async function addBusinessHour(): Promise<void> {
  if (!selectedPlace.value) {
    return
  }

  const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
  try {
    await request(`/owner/places/${selectedPlace.value.id}/business-hours`, {
      method: 'POST',
      body: JSON.stringify({
        dayOfWeek: days[hourDay.value - 1],
        opensAt: hourOpen.value,
        closesAt: hourClose.value,
      }),
    })
    message.value = 'Çalışma saati eklendi.'
  } catch (error) {
    message.value = (error as Error).message
  }
}

onMounted(async () => {
  await loadPlaces()
  await loadMine()
  refreshTimer = window.setInterval(loadMine, 30_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>

<template>
<div class="app-shell">
  <header class="topbar">
    <a class="brand" href="#top">
      <span class="brand-mark">
        S
      </span>
      slotly
    </a>
    <nav>
      <a href="#places">
        Mekânlar
      </a>
      <a href="#appointments" v-if="token">
        Randevularım
      </a>
      <a href="#waitlist" v-if="token&&role==='CUSTOMER'">
        Bekleme listesi
      </a>
    </nav>
    <button v-if="token" class="button quiet" @click="logout">
      Çıkış
    </button>
    <RouterLink v-else to="/login" class="button quiet">
      Giriş yap
    </RouterLink>
    <RouterLink v-if="!token" to="/register" class="button primary">
      Kayıt ol
    </RouterLink>
  </header>
  <main id="top">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">
          GÜNÜNÜ PLANLA
        </span>
        <h1>
          İyi hissetmek için
          <br>
          <em>
            zaman ayır.
          </em>
        </h1>
        <p>
          Yakınındaki işletmelerden sana uyan zamanı bul, randevunu kolayca oluştur.
        </p>
        <a class="button primary" href="#places">
          Mekânları keşfet
          <span>
            ↗
          </span>
        </a>
      </div>
      <div class="hero-note">
        <span class="sun">
          ✳
        </span>
        <div>
          <b>
            Planların netleşsin.
          </b>
          <small>
            Randevun birkaç adım uzağında.
          </small>
        </div>
      </div>
      <div class="hero-orb">
      </div>
    </section>
    <section id="account" class="account section" v-if="!token">
      <div>
        <span class="eyebrow">
          SLOTLY'YE KATIL
        </span>
        <h2>Randevularını tek yerden yönet.</h2>
        <p>
          Slotly hesabınla sana uygun zamanı bul ve planlarını kolayca takip et.
        </p>
      </div>
      <div class="account-actions">
        <RouterLink to="/login" class="button primary">Giriş yap</RouterLink>
        <RouterLink to="/register" class="text-button">Yeni hesap oluştur</RouterLink>
      </div>
    </section>
    <section class="section" v-if="isOwner">
      <div class="section-heading">
        <div>
          <span class="eyebrow">
            İŞLETME PANELİ
          </span>
          <h2>
            Mekânını ekle
          </h2>
        </div>
        <p>
          Yeni mekân oluştur, sonra listeden seçerek hizmet ekle.
        </p>
      </div>
      <form class="form-grid owner-form" @submit.prevent="createPlace">
        <label>
          Mekân adı
          <input v-model="placeName" required maxlength="120">
        </label>
        <label>
          Adres
          <input v-model="placeAddress" required maxlength="300">
        </label>
        <label>
          Açıklama
          <input v-model="placeDescription" maxlength="1000">
        </label>
        <label>
          Kapasite
          <input v-model.number="placeCapacity" type="number" min="1" max="10000" required>
        </label>
        <button class="button primary">
          Mekân oluştur
        </button>
      </form>
      <form v-if="selectedPlace" class="form-grid owner-form service-form" @submit.prevent="createOffering">
        <h3>
          {{selectedPlace.name}} · Hizmet veya ürün ekle
        </h3>
        <label>
          Tür
          <select v-model="offeringKind">
            <option value="SERVICE">
              Hizmet
            </option>
            <option value="PRODUCT">
              Ürün
            </option>
          </select>
        </label>
        <label>
          Ad
          <input v-model="serviceName" required>
        </label>
        <label>
          Ücret
          <input v-model.number="servicePrice" type="number" min="0" step="0.01" required>
        </label>
        <label v-if="offeringKind==='SERVICE'">
          Süre (dakika)
          <input v-model.number="serviceDuration" type="number" min="1" max="1440" required>
        </label>
        <button class="button primary">
          Ekle
        </button>
      </form>
      <form v-if="selectedPlace" class="form-grid owner-form service-form" @submit.prevent="addBusinessHour">
        <h3>
          Çalışma saati ekle
        </h3>
        <label>
          Gün
          <select v-model.number="hourDay">
            <option v-for="(day,index) in ['Pazartesi','Salı','Çarşamba','Perşembe','Cuma','Cumartesi','Pazar']" :key="day" :value="index+1">
              {{day}}
            </option>
          </select>
        </label>
        <label>
          Açılış
          <input v-model="hourOpen" type="time" required>
        </label>
        <label>
          Kapanış
          <input v-model="hourClose" type="time" required>
        </label>
        <button class="button primary">
          Saat ekle
        </button>
      </form>
    </section>
    <section id="places" class="section">
      <div class="section-heading">
        <div>
          <span class="eyebrow">
            KEŞFET
          </span>
          <h2>
            Sana uygun yeri bul
          </h2>
        </div>
        <p>
          İşletmelerin hizmetlerini incele, uygun zamanı seç.
        </p>
      </div>
      <div class="place-layout">
        <div class="place-list">
          <button v-for="(place,index) in places" :key="place.id" class="place-card" :class="{selected:selectedPlace?.id===place.id}" @click="choosePlace(place)">
            <span class="place-number">
              {{String(index+1).padStart(2,'0')}}
            </span>
            <span>
              <b>
                {{place.name}}
              </b>
              <small>
                {{place.address}}
              </small>
            </span>
            <span class="arrow">
              ↗
            </span>
          </button>
          <p v-if="!places.length" class="empty">
            Henüz listelenen mekân yok.
          </p>
        </div>
        <div class="detail-panel">
          <template v-if="selectedPlace">
            <div class="detail-head">
              <span class="eyebrow">
                {{selectedPlace.address}}
              </span>
              <h3>
                {{selectedPlace.name}}
              </h3>
              <p>
                {{selectedPlace.description||'Bu işletmenin hizmetlerinden birini seçerek uygun saatleri görüntüle.'}}
              </p>
            </div>
            <div class="offering-list">
              <button v-for="item in offerings" :key="item.id" class="offering" :class="{active:selectedOffering?.id===item.id}" @click="selectedOffering=item;loadSlots()">
                <span>
                  <b>
                    {{item.name}}
                  </b>
                  <small>
                    {{item.kind==='SERVICE'?`${item.durationMinutes} dk`:'Ürün'}}
                  </small>
                </span>
                <strong>
                  {{item.price.toFixed(2)}} ₺
                </strong>
              </button>
              <p v-if="!offerings.length" class="empty">
                Henüz hizmet veya ürün eklenmemiş.
              </p>
            </div>
            <div v-if="selectedOffering?.kind==='SERVICE'" class="availability">
              <label>
                Tarih
                <input v-model="date" type="date" @change="loadSlots">
              </label>
              <div class="slot-grid">
                <button v-for="slot in slots" :key="slot" class="slot" :disabled="!token||role!=='CUSTOMER'" @click="book(slot)">
                  {{new Date(slot).toLocaleTimeString('tr-TR',{hour:'2-digit',minute:'2-digit'})}}
                </button>
              </div>
              <p v-if="!slots.length" class="empty">
                Bir hizmet ve tarih seçerek uygun saatleri gör.
              </p>
              <small v-if="!token">
                Randevu almak için giriş yap.
              </small>
              <form v-if="token&&role==='CUSTOMER'" class="waitlist-join" @submit.prevent="joinWaitlist">
                <label>
                  İstediğin saat
                  <input v-model="desiredTime" type="time" required>
                </label>
                <button class="text-button">
                  Bu saat için bekleme listesine katıl
                </button>
              </form>
            </div>
          </template>
          <div v-else class="detail-empty">
            <span>
              ✳
            </span>
            <b>
              Bir mekân seç
            </b>
            <p>
              Hizmetleri ve uygun saatleri burada göreceksin.
            </p>
          </div>
        </div>
      </div>
    </section>
    <section id="owner-calendar" class="section" v-if="isOwner&&selectedPlace">
      <div class="section-heading">
        <div>
          <span class="eyebrow">
            İŞLETME TAKVİMİ
          </span>
          <h2>
            {{selectedPlace.name}} randevuları
          </h2>
        </div>
        <label>
          Tarih
          <input v-model="date" type="date" @change="loadOwnerAppointments">
        </label>
      </div>
      <div class="appointment-list">
        <article v-for="item in ownerAppointments" :key="item.id" class="appointment">
          <div class="appointment-date">
            <b>
              {{new Date(item.startsAt).toLocaleTimeString('tr-TR',{hour:'2-digit',minute:'2-digit'})}}
            </b>
          </div>
          <div>
            <b>
              {{item.offeringName}}
            </b>
            <small>
              {{item.customerName}} · {{new Date(item.startsAt).toLocaleDateString('tr-TR')}}
            </small>
          </div>
          <span class="status" :class="item.status.toLowerCase()">
            {{item.status==='CONFIRMED'?'Onaylandı':'İptal edildi'}}
          </span>
        </article>
        <p v-if="!ownerAppointments.length" class="empty">
          Bu tarihte randevu yok.
        </p>
      </div>
    </section>
    <section id="appointments" class="section" v-if="token">
      <div class="section-heading">
        <div>
          <span class="eyebrow">
            TAKVİMİN
          </span>
          <h2>
            Randevularım
          </h2>
        </div>
      </div>
      <div class="appointment-list">
        <article v-for="item in appointments" :key="item.id" class="appointment">
          <div class="appointment-date">
            <b>
              {{new Date(item.startsAt).toLocaleDateString('tr-TR',{day:'2-digit'})}}
            </b>
            <small>
              {{new Date(item.startsAt).toLocaleDateString('tr-TR',{month:'short'})}}
            </small>
          </div>
          <div>
            <b>
              {{item.offeringName}}
            </b>
            <small>
              {{item.placeName}} · {{new Date(item.startsAt).toLocaleTimeString('tr-TR',{hour:'2-digit',minute:'2-digit'})}}
            </small>
          </div>
          <span class="status" :class="item.status.toLowerCase()">
            {{item.status==='CONFIRMED'?'Onaylandı':'İptal edildi'}}
          </span>
          <button v-if="item.status==='CONFIRMED'" class="text-button cancel" @click="cancel(item.id)">
            İptal et
          </button>
        </article>
        <p v-if="!appointments.length" class="empty">
          Henüz randevun yok.
        </p>
      </div>
    </section>
    <section id="waitlist" class="section" v-if="token&&role==='CUSTOMER'">
      <div class="section-heading">
        <div>
          <span class="eyebrow">
            BEKLEME LİSTESİ
          </span>
          <h2>
            Tekliflerin
          </h2>
        </div>
        <p>
          Sıran geldiğinde 30 dakika içinde kabul edebilirsin.
        </p>
      </div>
      <div class="appointment-list">
        <article v-for="entry in waitlistEntries" :key="entry.id" class="appointment">
          <div class="appointment-date">
            <b>
              {{new Date(entry.startsAt).toLocaleDateString('tr-TR',{day:'2-digit'})}}
            </b>
            <small>
              {{new Date(entry.startsAt).toLocaleDateString('tr-TR',{month:'short'})}}
            </small>
          </div>
          <div>
            <b>
              {{entry.offeringName}}
            </b>
            <small>
              {{entry.placeName}} · {{new Date(entry.startsAt).toLocaleTimeString('tr-TR',{hour:'2-digit',minute:'2-digit'})}}
            </small>
            <small v-if="entry.status==='OFFERED'&&entry.offerExpiresAt">
              Teklif sonu: {{new Date(entry.offerExpiresAt).toLocaleTimeString('tr-TR',{hour:'2-digit',minute:'2-digit'})}}
            </small>
          </div>
          <span class="status">
            {{entry.status==='OFFERED'?'Teklif var':entry.status==='WAITING'?'Sırada':entry.status}}
          </span>
          <template v-if="entry.status==='OFFERED'">
            <button class="text-button" @click="acceptOffer(entry.id)">
              Kabul et
            </button>
            <button class="text-button cancel" @click="declineOffer(entry.id)">
              Reddet
            </button>
          </template>
          <button v-else-if="entry.status==='WAITING'" class="text-button cancel" @click="leaveWaitlist(entry.id)">
            Listeden ayrıl
          </button>
        </article>
        <p v-if="!waitlistEntries.length" class="empty">
          Henüz bekleme listesi kaydın yok.
        </p>
      </div>
    </section>
    <p class="toast" v-if="message" role="status">
      {{message}}
      <button @click="message=''" aria-label="Mesajı kapat">
        ×
      </button>
    </p>
  </main>
  <footer>
    <a class="brand" href="#top">
      <span class="brand-mark">
        S
      </span>
      slotly
    </a>
    <span>
      Randevular için iyi bir yer.
    </span>
    <small>
      © Slotly
    </small>
  </footer>
</div>
</template>

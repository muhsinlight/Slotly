<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { listPlaces, type Place } from '../lib/bookings'

const places = ref<Place[]>([])
const search = ref('')
const loading = ref(true)
const error = ref('')
const filteredPlaces = computed(() => {
  const query = search.value.trim().toLocaleLowerCase('tr-TR')
  return places.value.filter((place) => `${place.name} ${place.address}`.toLocaleLowerCase('tr-TR').includes(query))
})

async function loadPlaces(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    places.value = await listPlaces()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(loadPlaces)
</script>

<template>
  <div id="top" class="app-shell">
    <AppHeader />
    <main>
      <section class="hero">
        <div class="hero-copy">
          <span class="eyebrow">GÜNÜNÜ PLANLA</span>
          <h1>İyi hissetmek için<br><em>zaman ayır.</em></h1>
          <p>Yakınındaki işletmelerden sana uyan zamanı bul, randevunu kolayca oluştur.</p>
          <a class="button primary" href="#places">Mekânları keşfet <span>↗</span></a>
        </div>
        <div class="hero-note"><span class="sun">✳</span><div><b>Planların netleşsin.</b><small>Randevun birkaç adım uzağında.</small></div></div>
        <div class="hero-preview" aria-hidden="true">
          <div class="hero-preview-top"><span>✦</span><span>PROGRAMIN</span><span>•••</span></div>
          <div class="hero-preview-date">Bugün kendine<br><strong>zaman ayır.</strong></div>
          <div class="hero-preview-card"><span class="hero-preview-icon">◷</span><div><b>Yeni bir başlangıç</b><small>Sana uygun saatler bir arada</small></div><span>↗</span></div>
          <div class="hero-preview-days"><span>PZT</span><span>SAL</span><span class="active">ÇAR</span><span>PER</span><span>CUM</span></div>
        </div>
      </section>
      <section id="places" class="section">
        <div class="section-heading"><div><span class="eyebrow">KEŞFET</span><h2>Sana uygun yeri bul</h2></div><p>İşletmelerin hizmetlerini incele, uygun zamanı seç.</p></div>
        <label class="place-search"><span>Mekân ara</span><input v-model="search" type="search" placeholder="Mekân adı veya adres" autocomplete="off"></label>
        <p v-if="loading" class="empty" role="status">Mekânlar yükleniyor…</p>
        <p v-else-if="error" class="booking-error" role="alert">{{ error }} <button class="text-button" @click="loadPlaces">Yeniden dene</button></p>
        <div v-else class="place-list discovery-list">
          <RouterLink v-for="(place, index) in filteredPlaces" :key="place.id" class="place-card" :to="{ name: 'place', params: { id: place.id } }">
            <span class="place-number">{{ String(index + 1).padStart(2, '0') }}</span>
            <span><b>{{ place.name }}</b><small>{{ place.address }}</small></span><span class="arrow">↗</span>
          </RouterLink>
          <p v-if="!places.length" class="empty">Henüz listelenen mekân yok.</p>
          <p v-else-if="!filteredPlaces.length" class="empty">Aramana uygun mekân bulunamadı.</p>
        </div>
      </section>
    </main>
    <footer class="site-footer">
      <div class="site-footer-main">
        <div class="site-footer-identity">
          <RouterLink class="brand" to="/" aria-label="Slotly ana sayfa">
            <span class="brand-mark">S</span>
            slotly
          </RouterLink>
          <p>Yakınındaki mekânları keşfet, sana uygun zamanı seç.</p>
        </div>
        <nav aria-label="Alt menü">
          <a href="#places">Mekânları keşfet <span aria-hidden="true">↗</span></a>
          <a href="#top">Başa dön <span aria-hidden="true">↑</span></a>
        </nav>
      </div>
      <small>© {{ new Date().getFullYear() }} Slotly</small>
    </footer>
  </div>
</template>

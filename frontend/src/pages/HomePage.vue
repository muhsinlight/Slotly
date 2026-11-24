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
  <div id="top" class="app-shell home-page">
    <AppHeader />
    <main>
      <section class="home-hero">
        <div class="home-hero-copy">
          <h1>Sana uyan <span>saati bul.</span></h1>
          <p>İşletmeleri ve hizmetlerini incele, boş saatleri gör, randevunu gününe göre planla.</p>
          <a class="button primary home-hero-action" href="#places">
            Mekânları keşfet
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
              <path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </a>
          <p class="home-hero-hint">Mekânı seç. Hizmeti seç. Zamanını ayır.</p>
        </div>
        <div class="home-plan" aria-hidden="true">
          <div class="home-plan-heading">
            <span>Bir randevuya yer aç</span>
            <span>Örnek çizelge</span>
          </div>
          <div class="home-plan-track">
            <div class="home-plan-row">
              <span>09.00</span>
              <span class="home-plan-rule"></span>
            </div>
            <div class="home-plan-row">
              <span>11.00</span>
              <span class="home-plan-rule"></span>
            </div>
            <div class="home-plan-row home-plan-row-active">
              <span>14.30</span>
              <div>
                <small>Örnek saat</small>
                <strong>Zamanın burada</strong>
              </div>
            </div>
            <div class="home-plan-row">
              <span>17.00</span>
              <span class="home-plan-rule"></span>
            </div>
          </div>
          <p>Gerçek boş saatleri mekân sayfasında görebilirsin.</p>
        </div>
      </section>
      <section id="places" class="home-discovery">
        <div class="home-discovery-heading">
          <h2>Mekânını seç</h2>
          <p>İşletmeleri incele, sana uygun hizmeti ve zamanı bul.</p>
        </div>
        <label class="home-search">
          <span>Mekân ara</span>
          <span class="home-search-control">
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
              <circle cx="10.8" cy="10.8" r="6.8" stroke="currentColor" stroke-width="1.8" />
              <path d="m16 16 4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <input v-model="search" type="search" placeholder="Mekân adı veya adres" autocomplete="off">
          </span>
        </label>
        <p v-if="loading" class="empty" role="status">Mekânlar yükleniyor…</p>
        <p v-else-if="error" class="booking-error" role="alert">{{ error }} <button class="text-button" @click="loadPlaces">Yeniden dene</button></p>
        <div v-else class="home-place-list">
          <RouterLink v-for="place in filteredPlaces" :key="place.id" class="home-place" :to="{ name: 'place', params: { id: place.id } }">
            <span class="home-place-details">
              <b>{{ place.name }}</b>
              <small>{{ place.address }}</small>
            </span>
            <span class="home-place-link">
              İncele
              <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
                <path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
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

<style scoped>
.home-page {
  --home-ink: #241f2e;
  --home-muted: #635d6a;
  --home-line: #ddd9e2;
  --home-accent: var(--green);
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, .95fr);
  min-height: 570px;
  margin-top: 32px;
  overflow: hidden;
  border: 1px solid #e7e2da;
  border-radius: 20px;
  background: #f3f0ea;
}

.home-hero-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  min-width: 0;
  padding: clamp(36px, 5.5vw, 78px);
}

.home-hero h1 {
  max-width: 10ch;
  margin: 0 0 25px;
  color: var(--home-ink);
  font: 600 clamp(48px, 5.3vw, 76px)/1.06 Manrope, sans-serif;
  letter-spacing: -.065em;
}

.home-hero h1 span {
  display: block;
}

.home-hero-copy > p:not(.home-hero-hint) {
  max-width: 35ch;
  margin: 0 0 32px;
  color: #534e58;
  font-size: 17px;
  line-height: 1.65;
}

.home-hero-action {
  min-height: 50px;
  padding-inline: 20px;
  border-radius: 8px;
  box-shadow: none;
  font-size: 14px;
}

.home-hero-action svg,
.home-place-link svg {
  width: 20px;
  height: 20px;
  flex: none;
}

.home-hero-hint {
  margin: auto 0 0;
  padding-top: 44px;
  color: var(--home-muted);
  font-size: 12px;
  line-height: 1.5;
}

.home-plan {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 35px clamp(28px, 3.5vw, 52px);
  background: #272133;
  color: #f8f6fb;
}

.home-plan-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 18px;
  font-size: 15px;
  font-weight: 600;
}

.home-plan-heading span:last-child {
  color: #b9afc8;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.home-plan-track {
  display: grid;
  gap: 35px;
  margin: auto 0;
  padding-block: 48px;
}

.home-plan-row {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  color: #c6bed2;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.home-plan-rule {
  height: 1px;
  background: #554d61;
}

.home-plan-row-active {
  color: #fff;
}

.home-plan-row-active > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 19px 22px;
  border-left: 4px solid #bda8f2;
  border-radius: 7px;
  background: #f6f3fb;
  color: var(--home-ink);
  box-shadow: 0 18px 35px #17121e45;
}

.home-plan-row-active small {
  color: #6c6177;
  font-size: 11px;
}

.home-plan-row-active strong {
  font: 600 clamp(18px, 2vw, 26px)/1.2 Manrope, sans-serif;
  letter-spacing: -.03em;
}

.home-plan > p {
  max-width: 30ch;
  margin: 0;
  color: #b9afc8;
  font-size: 12px;
  line-height: 1.5;
}

.home-discovery {
  padding: 88px 0 36px;
}

.home-discovery-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--home-line);
}

.home-discovery-heading h2 {
  margin: 0;
  color: var(--home-ink);
  font: 600 clamp(32px, 3.3vw, 46px)/1.12 Manrope, sans-serif;
  letter-spacing: -.045em;
}

.home-discovery-heading p {
  max-width: 32ch;
  margin: 0 0 3px;
  color: var(--home-muted);
  font-size: 14px;
  line-height: 1.5;
}

.home-search {
  display: grid;
  gap: 10px;
  width: min(480px, 100%);
  margin: 35px 0 28px;
  color: var(--home-ink);
  font-size: 13px;
  font-weight: 600;
}

.home-search-control {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: 0 16px;
  border: 1px solid var(--home-line);
  border-radius: 9px;
  background: #fff;
  color: #746c7e;
}

.home-search-control:focus-within {
  border-color: var(--home-accent);
  outline: 2px solid #d4c8f6;
  outline-offset: 2px;
}

.home-search-control svg {
  width: 19px;
  height: 19px;
  flex: none;
}

.home-search input {
  width: 100%;
  min-width: 0;
  min-height: 52px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--home-ink);
  font: inherit;
  font-weight: 400;
}

.home-search input::placeholder {
  color: #817a89;
}

.home-place-list {
  border-top: 1px solid var(--home-line);
}

.home-place {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  min-height: 96px;
  padding: 20px 6px;
  border-bottom: 1px solid var(--home-line);
  transition: background-color .18s ease, padding-inline .18s ease;
}

.home-place:hover,
.home-place:focus-visible {
  padding-inline: 16px;
  background: #f1edf8;
}

.home-place-details {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.home-place-details b {
  color: var(--home-ink);
  font: 600 19px/1.25 Manrope, sans-serif;
}

.home-place-details small {
  color: var(--home-muted);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.home-place-link {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex: none;
  color: var(--home-accent);
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .home-hero {
    grid-template-columns: 1fr;
  }

  .home-hero-copy {
    min-height: 480px;
  }

  .home-plan {
    min-height: 430px;
  }
}

@media (max-width: 580px) {
  .home-hero {
    margin-top: 28px;
    border-radius: 16px;
  }

  .home-hero-copy {
    min-height: 0;
    padding: 38px 26px 34px;
  }

  .home-hero h1 {
    font-size: clamp(42px, 11vw, 54px);
  }

  .home-hero-copy > p:not(.home-hero-hint) {
    font-size: 15px;
  }

  .home-hero-hint {
    padding-top: 38px;
  }

  .home-plan {
    min-height: 390px;
    padding: 27px 24px;
  }

  .home-plan-track {
    gap: 26px;
    padding-block: 30px;
  }

  .home-plan-row {
    grid-template-columns: 55px minmax(0, 1fr);
    gap: 10px;
  }

  .home-plan-row-active > div {
    padding: 15px 14px;
  }

  .home-discovery {
    padding: 68px 0 24px;
  }

  .home-discovery-heading {
    display: block;
  }

  .home-discovery-heading p {
    margin-top: 13px;
  }

  .home-place {
    min-height: 88px;
    gap: 12px;
  }

  .home-place-details b {
    font-size: 16px;
  }

  .home-place-link {
    gap: 5px;
    font-size: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-place {
    transition: none;
  }
}
</style>

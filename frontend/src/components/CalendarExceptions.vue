<script setup lang="ts">
import { ref, watch } from 'vue'
import { request } from '../lib/api'

type CalendarException = { id: string; date: string; closed: boolean; opensAt: string | null; closesAt: string | null }

const props = defineProps<{ placeId: string }>()
const exceptions = ref<CalendarException[]>([])
const form = ref({ date: '', closed: true, opensAt: '09:00', closesAt: '17:00' })
const busy = ref(false)
const error = ref('')
const notice = ref('')

async function load(): Promise<void> {
  const placeId = props.placeId
  try {
    const result = await request<CalendarException[]>(`/owner/places/${placeId}/calendar-exceptions`)
    if (placeId === props.placeId) exceptions.value = result
  } catch (cause) {
    if (placeId === props.placeId) error.value = (cause as Error).message
  }
}

async function save(): Promise<void> {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await request(`/owner/places/${props.placeId}/calendar-exceptions/${form.value.date}`, {
      method: 'PUT',
      body: JSON.stringify({
        closed: form.value.closed,
        opensAt: form.value.closed ? null : form.value.opensAt,
        closesAt: form.value.closed ? null : form.value.closesAt,
      }),
    })
    notice.value = 'Özel gün kaydedildi.'
    await load()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

async function remove(exception: CalendarException): Promise<void> {
  if (!window.confirm(`${exception.date} özel gününü kaldırıp haftalık çalışma saatlerine dönmek istiyor musun?`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await request(`/owner/places/${props.placeId}/calendar-exceptions/${exception.date}`, { method: 'DELETE' })
    notice.value = 'Özel gün kaldırıldı; haftalık çalışma saatleri geçerli.'
    await load()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}

function edit(exception: CalendarException): void {
  form.value = {
    date: exception.date,
    closed: exception.closed,
    opensAt: exception.opensAt?.slice(0, 5) || '09:00',
    closesAt: exception.closesAt?.slice(0, 5) || '17:00',
  }
}

watch(() => props.placeId, () => {
  exceptions.value = []
  error.value = ''
  notice.value = ''
  void load()
}, { immediate: true })
</script>

<template>
  <section class="owner-card" aria-labelledby="calendar-exceptions-title">
    <div class="owner-section-heading">
      <div><span class="eyebrow">TATİL VE ÖZEL SAATLER</span><h2 id="calendar-exceptions-title">Özel günler</h2></div>
    </div>
    <p>Özel günler haftalık saatlerin yerine geçer. Mevcut randevu veya aktif teklifle çakışan değişiklikler kaydedilmez.</p>
    <p v-if="error" class="booking-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="owner-notice" role="status">{{ notice }}</p>
    <div class="owner-list">
      <article v-for="exception in exceptions" :key="exception.id" class="owner-list-row">
        <div><b>{{ exception.date }}</b><small>{{ exception.closed ? 'Kapalı' : `${exception.opensAt?.slice(0, 5)} – ${exception.closesAt?.slice(0, 5)}` }}</small></div>
        <div class="owner-actions">
          <button class="text-button" :disabled="busy" @click="edit(exception)">Düzenle</button>
          <button class="text-button cancel" :disabled="busy" @click="remove(exception)">Kaldır</button>
        </div>
      </article>
      <p v-if="!exceptions.length" class="empty">Özel gün tanımlanmamış.</p>
    </div>
    <form class="owner-form-grid owner-editor" @submit.prevent="save">
      <h3>Özel gün kaydet</h3>
      <label>Özel gün tarihi<input v-model="form.date" type="date" required :disabled="busy"></label>
      <label class="owner-checkbox"><input v-model="form.closed" type="checkbox" :disabled="busy">Bu gün kapalı</label>
      <template v-if="!form.closed">
        <label>Özel açılış<input v-model="form.opensAt" type="time" required :disabled="busy"></label>
        <label>Özel kapanış<input v-model="form.closesAt" type="time" required :disabled="busy"></label>
      </template>
      <button class="button primary" :disabled="busy">{{ busy ? 'Kaydediliyor…' : 'Özel günü kaydet' }}</button>
    </form>
  </section>
</template>

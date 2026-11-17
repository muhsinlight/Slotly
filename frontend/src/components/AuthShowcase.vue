<script setup lang="ts">
const previewDays = [
  { label: 'Pzt', date: 9 },
  { label: 'Sal', date: 10 },
  { label: 'Çar', date: 11 },
  { label: 'Per', date: 12 },
  { label: 'Cum', date: 13 },
  { label: 'Cmt', date: 14 },
  { label: 'Paz', date: 15 },
]
</script>

<template>
  <aside class="auth-showcase" aria-labelledby="auth-showcase-title">
    <div class="showcase-content">
      <div class="showcase-heading">
        <span class="showcase-eyebrow">HAYATINA YER AÇ</span>
        <h2 id="auth-showcase-title">
          Planların net.<br>
          <span>Günün sana kalsın.</span>
        </h2>
        <p>Sevdiğin yerlere, kendine ayırdığın zamana.<br>Bir sonraki randevun Slotly’de.</p>
      </div>

      <figure class="showcase-preview" aria-labelledby="showcase-preview-caption">
        <div class="preview-calendar">
          <div class="preview-calendar-heading">
            <div>
              <span class="preview-overline">SENİN TAKVİMİN</span>
              <h3>Küçük bir mola.</h3>
            </div>
            <svg class="preview-calendar-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <rect x="4" y="5" width="16" height="16" rx="3" />
              <path d="M8 3v4m8-4v4M4 11h16m-11 5 2 2 4-4" />
            </svg>
          </div>

          <div class="preview-month">
            <span>Kasım 2026</span>
            <span>9 – 15</span>
          </div>

          <div class="preview-week" aria-label="Örnek hafta: 12 Kasım Perşembe seçili">
            <div
              v-for="day in previewDays"
              :key="day.date"
              class="preview-day"
              :class="{ 'preview-day-selected': day.date === 12 }"
            >
              <span>{{ day.label }}</span>
              <strong>{{ day.date }}</strong>
              <span class="preview-day-dot" aria-hidden="true" />
            </div>
          </div>

          <div class="preview-appointment">
            <span class="preview-time">10:00</span>
            <div class="preview-appointment-detail">
              <strong>Bakım zamanı</strong>
              <span>45 dakika · Sadece kendin için</span>
            </div>
            <span class="preview-confirmed" aria-label="Randevu onaylandı">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <path d="m5 10 3 3 7-7" />
              </svg>
            </span>
          </div>
        </div>

        <div class="preview-offer">
          <span class="preview-offer-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="m13 3-8 11h6l-1 7 9-12h-7l1-6Z" />
            </svg>
          </span>
          <div class="preview-offer-copy">
            <span>BEKLEME LİSTESİ</span>
            <strong>Beklediğin saat boşaldı.</strong>
            <p>Teklifini 30 dakika içinde kabul et.</p>
          </div>
          <span class="preview-offer-dot" aria-hidden="true" />
        </div>

        <figcaption id="showcase-preview-caption">Slotly ile örnek bir gün</figcaption>
      </figure>

      <div class="showcase-footer">
        <span class="showcase-spark" aria-hidden="true">✳</span>
        <p>Doğru yer. Uygun zaman.<br><strong>Gerisi sana kalmış.</strong></p>
        <span class="showcase-signature" aria-hidden="true">s.</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.auth-showcase {
  position: relative;
  isolation: isolate;
  display: grid;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  padding: 52px 42px 40px;
  color: #f7f8ed;
  background: #392176;
}

.auth-showcase::before,
.auth-showcase::after {
  position: absolute;
  z-index: -1;
  pointer-events: none;
  content: '';
}

.auth-showcase::before {
  inset: 0;
  opacity: .28;
  background-image: radial-gradient(#d1e0c2 0.65px, transparent 0.65px);
  background-size: 16px 16px;
  mask-image: linear-gradient(150deg, transparent 10%, #000 70%);
}

.auth-showcase::after {
  width: 620px;
  height: 620px;
  top: 23%;
  left: 12%;
  border: 1px solid #d3e2c51a;
  border-radius: 50%;
  box-shadow: 0 0 0 60px #d3e2c508, 0 0 0 120px #d3e2c505;
  background: radial-gradient(circle, #6c8d5d35, transparent 65%);
}

.showcase-content {
  width: 100%;
  max-width: 390px;
  margin: 0 auto;
}

.showcase-eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #d7e3c5;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 2px;
}

.showcase-eyebrow::before {
  width: 22px;
  height: 1px;
  background: #d9b6ff;
  content: '';
}

.showcase-heading h2 {
  margin: 22px 0 15px;
  font: 500 clamp(29px, 3.1vw, 38px)/1.2 Manrope, sans-serif;
  letter-spacing: -1.6px;
}

.showcase-heading h2 span {
  color: #d9e5bc;
}

.showcase-heading p {
  margin: 0;
  color: #c5d4c6;
  font-size: 13px;
  line-height: 1.75;
}

.showcase-preview {
  margin: 34px 0 28px;
}

.preview-calendar {
  padding: 24px 22px 40px;
  color: #302348;
  background: #fffaff;
  border: 1px solid #fff;
  border-radius: 14px;
  box-shadow: 0 16px 36px #102d282e;
  transform: rotate(-2deg);
}

.preview-calendar-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-overline {
  color: #6b7668;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 1.5px;
}

.preview-calendar-heading h3 {
  margin: 7px 0 0;
  font: 700 21px/1.2 Manrope, sans-serif;
  letter-spacing: -.7px;
}

.preview-calendar-icon {
  width: 25px;
  height: 25px;
  color: #5d765d;
  stroke: currentColor;
  stroke-width: 1.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.preview-month {
  display: flex;
  justify-content: space-between;
  margin: 25px 0 14px;
  color: #657160;
  font-size: 10px;
}

.preview-month span:first-child {
  color: #453463;
  font-weight: 600;
}

.preview-week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}

.preview-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 0 7px;
  border-radius: 20px;
  color: #737c6e;
  font-size: 9px;
}

.preview-day strong {
  color: #374933;
  font-size: 13px;
  font-weight: 600;
}

.preview-day-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
}

.preview-day-selected {
  color: #e0eacb;
  background: #6742cc;
}

.preview-day-selected strong {
  color: #fff;
}

.preview-day-selected .preview-day-dot {
  background: #e6d4ff;
}

.preview-appointment {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  padding: 15px 11px;
  border: 1px solid #e0e7d7;
  border-radius: 8px;
  background: #f0eaff;
}

.preview-time {
  align-self: stretch;
  display: flex;
  align-items: center;
  padding-right: 10px;
  border-right: 1px solid #ced9c3;
  color: #6742cc;
  font-size: 11px;
  font-weight: 600;
}

.preview-appointment-detail {
  display: grid;
  gap: 5px;
}

.preview-appointment-detail strong {
  font-size: 11px;
}

.preview-appointment-detail > span {
  color: #65735c;
  font-size: 9px;
  line-height: 1.5;
}

.preview-confirmed {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  margin-left: auto;
  border-radius: 50%;
  background: #e1d4fa;
  color: #6742cc;
}

.preview-confirmed svg {
  width: 15px;
  height: 15px;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.preview-offer {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: calc(100% - 6px);
  margin: -17px -12px 0 auto;
  padding: 18px 16px;
  color: #453463;
  background: #eae0fc;
  border: 1px solid #f4f6e8;
  border-radius: 10px;
  box-shadow: 0 12px 28px #102d2833;
  transform: rotate(2deg);
}

.preview-offer-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border: 1px solid #cad7ae;
  border-radius: 11px;
  background: #dce6bf;
}

.preview-offer-icon svg {
  width: 20px;
  height: 20px;
  stroke: #4e693c;
  stroke-width: 1.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.preview-offer-copy {
  display: grid;
  gap: 5px;
}

.preview-offer-copy > span {
  color: #647449;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 1.2px;
}

.preview-offer-copy strong {
  font-size: 12px;
  line-height: 1.4;
}

.preview-offer-copy p {
  margin: 0;
  color: #5c6e48;
  font-size: 10px;
  line-height: 1.5;
}

.preview-offer-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  align-self: flex-start;
  margin: 2px 0 0 auto;
  border-radius: 50%;
  background: #5e7b3f;
}

.showcase-preview figcaption {
  margin-top: 19px;
  color: #c4d3be;
  font-size: 10px;
  text-align: center;
  letter-spacing: .3px;
}

.showcase-footer {
  display: flex;
  align-items: center;
  gap: 13px;
  padding-top: 22px;
  border-top: 1px solid #d3e2c52b;
}

.showcase-spark {
  color: #d4dba1;
  font-size: 32px;
  line-height: 1;
}

.showcase-footer p {
  margin: 0;
  color: #c5d4c6;
  font-size: 11px;
  line-height: 1.7;
}

.showcase-footer strong {
  color: #edf1de;
  font-weight: 500;
}

.showcase-signature {
  margin-left: auto;
  color: #d6e2c5;
  font: 500 38px/1 Georgia, serif;
  letter-spacing: -3px;
}

@media (max-width: 1000px) {
  .auth-showcase {
    padding: 42px 28px 32px;
  }

  .preview-calendar {
    padding: 20px 15px 36px;
  }

  .preview-offer {
    gap: 9px;
    padding: 15px 12px;
  }

  .preview-offer-dot {
    display: none;
  }
}

@media (max-width: 760px) {
  .auth-showcase {
    display: none;
  }
}
</style>

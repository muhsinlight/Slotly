<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { unreadOffers, useWaitlistFeed } from './composables/waitlist'

const route = useRoute()
useWaitlistFeed()
</script>

<template>
  <RouterView />
  <aside v-if="unreadOffers && route.path !== '/waitlist'" class="offer-notification" role="status">
    <b>{{ unreadOffers }} yeni randevu teklifin var.</b>
    <RouterLink to="/waitlist">Teklifi incele →</RouterLink>
  </aside>
</template>

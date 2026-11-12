<script setup lang="ts">
import AuthShowcase from './AuthShowcase.vue'

defineProps<{
  eyebrow: string
  title: string
  description: string
}>()
</script>

<template>
  <main class="auth-page">
    <section class="auth-frame">
      <div class="auth-card">
        <RouterLink class="brand auth-brand" to="/">
          <span class="brand-mark">S</span>
          slotly
        </RouterLink>

        <div class="auth-heading">
          <span class="eyebrow">{{ eyebrow }}</span>
          <h1>{{ title }}</h1>
          <p>{{ description }}</p>
        </div>

        <slot />

        <div class="auth-switch">
          <slot name="switch" />
        </div>
      </div>

      <AuthShowcase />
    </section>

    <footer class="auth-footer">
      <span>© Slotly</span>
      <span>Randevular için iyi bir yer.</span>
    </footer>
  </main>
</template>

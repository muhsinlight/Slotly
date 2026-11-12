<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthPageLayout from '../components/AuthPageLayout.vue'
import type { Role } from '../composables/auth'
import { saveAuth } from '../composables/auth'
import { request } from '../lib/api'

const router = useRouter()
const username = ref('')
const password = ref('')
const firstName = ref('')
const lastName = ref('')
const role = ref<Role>('CUSTOMER')
const busy = ref(false)
const errorMessage = ref('')

async function register(): Promise<void> {
  busy.value = true
  errorMessage.value = ''

  try {
    await request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: username.value,
        password: password.value,
        firstName: firstName.value,
        lastName: lastName.value,
        role: role.value,
      }),
    })

    const response = await request<{ accessToken: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.value, password: password.value }),
    })

    saveAuth(response.accessToken)
    await router.push('/')
  } catch (error) {
    errorMessage.value = (error as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthPageLayout
    eyebrow="SLOTLY’YE KATIL"
    title="Hesabını oluştur"
    description="Sana uygun zamanı bulmak ve planlarını yönetmek için aramıza katıl."
  >
    <form class="auth-form auth-register-form" @submit.prevent="register">
      <div class="auth-name-fields">
        <label>
          Ad
          <input v-model="firstName" type="text" required maxlength="80" autocomplete="given-name" placeholder="Adın">
        </label>
        <label>
          Soyad
          <input v-model="lastName" type="text" required maxlength="80" autocomplete="family-name" placeholder="Soyadın">
        </label>
      </div>

      <label>
        Kullanıcı adı
        <input
          v-model="username"
          type="text"
          required
          maxlength="40"
          autocomplete="username"
          placeholder="Bir kullanıcı adı seç"
        >
      </label>

      <label>
        Parola
        <input
          v-model="password"
          type="password"
          required
          minlength="10"
          maxlength="100"
          autocomplete="new-password"
          placeholder="En az 10 karakter"
        >
      </label>

      <label>
        Hesap türü
        <select v-model="role">
          <option value="CUSTOMER">Müşteri</option>
          <option value="OWNER">İşletme sahibi</option>
        </select>
      </label>

      <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

      <button class="button primary auth-submit" type="submit" :disabled="busy">
        {{ busy ? 'Hesap oluşturuluyor…' : 'Hesap oluştur' }}
        <span aria-hidden="true">↗</span>
      </button>
    </form>

    <template #switch>
      <span>Zaten hesabın var mı?</span>
      <RouterLink to="/login">Giriş yap</RouterLink>
    </template>
  </AuthPageLayout>
</template>

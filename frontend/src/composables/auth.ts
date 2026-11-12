import { ref } from 'vue'

export type Role = 'OWNER' | 'CUSTOMER'

const TOKEN_KEY = 'slotly-token'

export const token = ref(localStorage.getItem(TOKEN_KEY) || '')
export const role = ref<Role>(readRole(token.value))

function readRole(accessToken: string): Role {
  try {
    const encodedClaims = accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(encodedClaims))

    if (claims.role === 'OWNER' || claims.role === 'CUSTOMER') {
      return claims.role
    }
  } catch {
    // Treat a missing or invalid token as a customer session.
  }

  return 'CUSTOMER'
}

export function saveAuth(accessToken: string): void {
  token.value = accessToken
  role.value = readRole(accessToken)
  localStorage.setItem(TOKEN_KEY, accessToken)
}

export function clearAuth(): void {
  token.value = ''
  role.value = 'CUSTOMER'
  localStorage.removeItem(TOKEN_KEY)
}

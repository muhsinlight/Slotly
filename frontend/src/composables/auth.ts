import { ref } from 'vue'

export type Role = 'OWNER' | 'CUSTOMER' | 'ADMIN'

const TOKEN_KEY = 'slotly-token'

export const token = ref(localStorage.getItem(TOKEN_KEY) || '')
export const role = ref<Role>(readRole(token.value))

export function isTokenExpired(accessToken: string): boolean {
  try {
    const encodedClaims = accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(encodedClaims))
    return typeof claims.exp !== 'number' || claims.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

function readRole(accessToken: string): Role {
  try {
    const encodedClaims = accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(encodedClaims))

    if (claims.role === 'OWNER' || claims.role === 'CUSTOMER' || claims.role === 'ADMIN') {
      return claims.role
    }
  } catch {
    // Treat a missing or invalid token as a customer session.
  }

  return 'CUSTOMER'
}

export function saveAuth(accessToken: string): void {
  if (isTokenExpired(accessToken)) {
    clearAuth()
    throw new Error('Oturum anahtarı geçersiz veya süresi dolmuş.')
  }

  token.value = accessToken
  role.value = readRole(accessToken)
  localStorage.setItem(TOKEN_KEY, accessToken)
}

if (token.value && isTokenExpired(token.value)) {
  clearAuth()
}

export function clearAuth(): void {
  token.value = ''
  role.value = 'CUSTOMER'
  localStorage.removeItem(TOKEN_KEY)
}

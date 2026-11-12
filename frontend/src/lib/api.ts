import { clearAuth, token } from '../composables/auth'

const API_BASE_URL = 'http://localhost:8080/api'

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token.value ? { Authorization: `Bearer ${token.value}` } : {}),
      ...options.headers,
    },
  })
  const body = response.status === 204 ? null : await response.json().catch(() => null)

  if (!response.ok) {
    if (response.status === 401 && token.value) {
      clearAuth()
      window.dispatchEvent(new CustomEvent('slotly:unauthorized'))
    }

    throw new Error(body?.message || body?.reason || `İstek başarısız (${response.status})`)
  }

  return (body && typeof body === 'object' && 'data' in body ? body.data : body) as T
}

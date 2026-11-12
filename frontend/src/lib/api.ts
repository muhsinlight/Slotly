import { clearAuth, token } from '../composables/auth'

const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
export const API_BASE_URL = `${API_ORIGIN}/api`

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const accessToken = token.value
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...options.headers,
    },
  }).catch((cause: unknown) => {
    if (options.signal?.aborted) throw cause
    throw new Error('Sunucuya ulaşılamadı. Bağlantını kontrol edip yeniden dene.')
  })
  const body = response.status === 204 ? null : await response.json().catch(() => null)

  if (!response.ok) {
    if (response.status === 401 && accessToken && token.value === accessToken) {
      clearAuth()
      window.dispatchEvent(new CustomEvent('slotly:unauthorized'))
    }

    const messages: Record<number, string> = {
      401: 'Oturum açman gerekiyor. Kullanıcı adını ve parolanı kontrol et.',
      403: 'Bu işlem için yetkin bulunmuyor.',
      429: 'Çok fazla giriş denemesi yapıldı. Biraz bekleyip yeniden dene.',
      500: 'İşlem tamamlanamadı. Biraz sonra yeniden dene.',
    }
    throw new ApiError(messages[response.status] || body?.message || body?.reason || `İstek başarısız (${response.status})`, response.status)
  }

  return (body && typeof body === 'object' && 'data' in body ? body.data : body) as T
}

import { clearAuth, token } from '../composables/auth'

const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
export const API_BASE_URL = `${API_ORIGIN}/api`

type ApiFailureBody = {
  code?: string
  message?: string
  reason?: string
  data?: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly details?: Record<string, string>

  constructor(message: string, status: number, code?: string, details?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
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
  const body = response.status === 204 ? null : await response.json().catch(() => null) as ApiFailureBody | null

  if (!response.ok) {
    if (response.status === 401 && accessToken && token.value === accessToken) {
      clearAuth()
      window.dispatchEvent(new CustomEvent('slotly:unauthorized'))
    }

    const rateLimitMessage = body?.message && !/^Login rate limit/i.test(body.message)
      ? body.message
      : path === '/auth/login'
        ? 'Çok fazla giriş denemesi yapıldı. Biraz bekleyip yeniden dene.'
        : 'Çok fazla istek gönderildi. Biraz bekleyip yeniden dene.'
    const messages: Record<number, string> = {
      401: 'Oturum açman gerekiyor. Kullanıcı adını ve parolanı kontrol et.',
      403: 'Bu işlem için yetkin bulunmuyor.',
      429: rateLimitMessage,
      500: 'İşlem tamamlanamadı. Biraz sonra yeniden dene.',
    }
    const details = body?.code === 'VALIDATION_ERROR' && body.data && typeof body.data === 'object'
      ? body.data as Record<string, string>
      : undefined
    throw new ApiError(
      messages[response.status] || body?.message || body?.reason || `İstek başarısız (${response.status})`,
      response.status,
      body?.code,
      details,
    )
  }

  return (body && typeof body === 'object' && 'data' in body ? body.data : body) as T
}

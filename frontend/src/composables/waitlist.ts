import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { clearAuth, isTokenExpired, role, token } from './auth'
import { API_BASE_URL, request } from '../lib/api'

export type WaitlistEntry = {
  id: string
  placeId: string
  placeName: string
  offeringId: string
  offeringName: string
  startsAt: string
  endsAt: string
  status: 'WAITING' | 'OFFERED' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'LEFT'
  offerExpiresAt: string | null
}

export const entries = ref<WaitlistEntry[]>([])
export const feedLoading = ref(false)
export const feedError = ref('')
export const now = ref(Date.now())
const readIds = ref<string[]>([])

export function deadline(entry: WaitlistEntry): number {
  const start = Date.parse(entry.startsAt)
  return entry.status === 'OFFERED' ? Math.min(start, entry.offerExpiresAt ? Date.parse(entry.offerExpiresAt) : 0) : start
}

export function statusAt(entry: WaitlistEntry, timestamp: number): WaitlistEntry['status'] {
  return (entry.status === 'WAITING' || entry.status === 'OFFERED') && timestamp >= deadline(entry)
    ? 'EXPIRED' : entry.status
}

export const activeOffers = computed(() => entries.value.filter((entry) => statusAt(entry, now.value) === 'OFFERED'))
export const unreadOffers = computed(() => activeOffers.value.filter((entry) => !readIds.value.includes(entry.id)).length)

let storageKey = ''
let sessionVersion = 0
let sessionController: AbortController | undefined
let inFlight: Promise<void> | undefined

export async function refreshEntries(): Promise<void> {
  if (!token.value || role.value !== 'CUSTOMER' || !sessionController) return
  if (inFlight) return inFlight
  const version = sessionVersion
  const signal = sessionController.signal
  const operation = request<WaitlistEntry[]>('/waitlist/me', { signal })
    .then((result) => {
      if (version !== sessionVersion || signal.aborted) return
      entries.value = result
      feedError.value = ''
    })
    .catch((cause: unknown) => {
      if (version === sessionVersion && !signal.aborted) feedError.value = (cause as Error).message
    })
    .finally(() => {
      if (version === sessionVersion) {
        feedLoading.value = false
        inFlight = undefined
      }
    })
  inFlight = operation
  return operation
}

export function markOffersRead(): void {
  readIds.value = activeOffers.value.map((entry) => entry.id)
  if (!storageKey) return
  try {
    localStorage.setItem(storageKey, JSON.stringify(readIds.value))
  } catch {
    // The current tab still retains read state when browser storage is unavailable.
  }
}

function readStoredOffers(accessToken: string): void {
  try {
    const payload = JSON.parse(atob(accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    storageKey = 'slotly-read-offers-' + payload.sub
    const stored: unknown = JSON.parse(localStorage.getItem(storageKey) || '[]')
    readIds.value = Array.isArray(stored) ? stored.filter((id): id is string => typeof id === 'string').slice(-500) : []
  } catch {
    storageKey = ''
    readIds.value = []
  }
}

function waitForRetry(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const finish = () => {
      window.clearTimeout(timer)
      signal.removeEventListener('abort', finish)
      resolve()
    }
    const timer = window.setTimeout(finish, milliseconds)
    signal.addEventListener('abort', finish, { once: true })
    if (signal.aborted) finish()
  })
}

async function connectEvents(accessToken: string, signal: AbortSignal): Promise<void> {
  const response = await fetch(API_BASE_URL + '/waitlist/events', {
    headers: { Authorization: 'Bearer ' + accessToken, Accept: 'text/event-stream' },
    signal,
  })
  if (response.status === 401 && token.value === accessToken) {
    clearAuth()
    window.dispatchEvent(new CustomEvent('slotly:unauthorized'))
  }
  if (!response.ok || !response.body) throw new Error('Offer connection unavailable')
  void refreshEntries()
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = ''
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read()
      if (done) return
      buffer += decoder.decode(value, { stream: true })
      if (buffer.length > 65_536) throw new Error('Invalid offer event')
      let boundary = buffer.indexOf('\n')
      while (boundary >= 0) {
        const line = buffer.slice(0, boundary).replace(/\r$/, '')
        buffer = buffer.slice(boundary + 1)
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        if (line === '') {
          if (eventName === 'offer') {
            await refreshEntries()
          }
          eventName = ''
        }
        boundary = buffer.indexOf('\n')
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}

async function maintainEvents(accessToken: string, signal: AbortSignal): Promise<void> {
  let retryDelay = 1000
  while (!signal.aborted) {
    const startedAt = Date.now()
    try {
      await connectEvents(accessToken, signal)
    } catch {
      // Polling continues during connection failures.
    }
    if (signal.aborted) return
    if (Date.now() - startedAt > 30_000) retryDelay = 1000
    await waitForRetry(retryDelay, signal)
    retryDelay = Math.min(retryDelay * 2, 30_000)
  }
}

export function useWaitlistFeed(): void {
  const stop = watch([token, role], ([accessToken, currentRole], _, onCleanup) => {
    sessionVersion++
    sessionController?.abort()
    sessionController = undefined
    inFlight = undefined
    entries.value = []
    feedError.value = ''
    feedLoading.value = false
    readIds.value = []
    storageKey = ''
    if (!accessToken || currentRole !== 'CUSTOMER') return

    const controller = new AbortController()
    sessionController = controller
    feedLoading.value = true
    readStoredOffers(accessToken)
    void refreshEntries()
    void maintainEvents(accessToken, controller.signal)
    const stopDeadlines = watch(entries, (list, _, onInvalidate) => {
      let timer: number
      function schedule(): void {
        now.value = Date.now()
        const next = list.reduce((earliest, entry) => {
          if (entry.status !== 'WAITING' && entry.status !== 'OFFERED') return earliest
          const end = deadline(entry)
          return end > now.value ? Math.min(earliest, end) : earliest
        }, Infinity)
        if (Number.isFinite(next)) {
          timer = window.setTimeout(() => {
            if (Date.now() >= next) void refreshEntries()
            schedule()
          }, Math.min(next - now.value, 2_147_483_647))
        }
      }
      schedule()
      onInvalidate(() => window.clearTimeout(timer))
    })
    const refreshTimer = window.setInterval(() => { void refreshEntries() }, 30_000)
    const clockTimer = window.setInterval(() => {
      now.value = Date.now()
      if (isTokenExpired(accessToken)) {
        clearAuth()
        window.dispatchEvent(new CustomEvent('slotly:unauthorized'))
        return
      }
    }, 1000)
    onCleanup(() => {
      controller.abort()
      stopDeadlines()
      window.clearInterval(refreshTimer)
      window.clearInterval(clockTimer)
    })
  }, { immediate: true })
  onBeforeUnmount(stop)
}

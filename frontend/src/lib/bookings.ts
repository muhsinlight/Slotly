import { request } from './api'
import { calendarRange } from './businessTime'

export type Place = {
  id: string
  name: string
  description: string | null
  address: string
  capacity: number
  active: boolean
  timeZone: string
}

export type Offering = {
  id: string
  name: string
  description: string | null
  price: number
  durationMinutes: number | null
  kind: 'SERVICE' | 'PRODUCT'
  active: boolean
}

export type Appointment = {
  id: string
  placeId: string
  placeName: string
  offeringName: string
  startsAt: string
  endsAt: string
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'
}

export type BookingSelection = { placeId: string; offeringId: string; startsAt: string }

export const listPlaces = () => request<Place[]>('/places')
export const getPlace = (id: string) => request<Place>(`/places/${id}`)
export const listOfferings = (id: string) => request<Offering[]>(`/places/${id}/offerings`)
export const listAppointments = () => request<Appointment[]>('/appointments/me')
export const cancelAppointment = (id: string) => request<void>(`/appointments/${id}`, { method: 'DELETE' })
export const createAppointment = (selection: BookingSelection) => request<Appointment>('/appointments', {
  method: 'POST', body: JSON.stringify(selection),
})
export const joinWaitlist = (selection: BookingSelection) => request('/waitlist', {
  method: 'POST', body: JSON.stringify(selection),
})

export function listSlots(place: Place, offeringId: string, date: string, signal?: AbortSignal): Promise<string[]> {
  const query = new URLSearchParams({ offeringId, ...calendarRange(date, place.timeZone) })
  return request<string[]>(`/places/${place.id}/availability?${query}`, { signal })
}

export const appointmentStatusLabels: Record<Appointment['status'], string> = {
  CONFIRMED: 'Onaylandı',
  CANCELLED: 'İptal edildi',
  COMPLETED: 'Tamamlandı',
  NO_SHOW: 'Gelmedi',
}

import { request } from './api'

export type Place = {
  id: string
  name: string
  description: string | null
  address: string
  capacity: number
  active: boolean
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
  status: 'CONFIRMED' | 'CANCELLED'
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

export function localDate(): string {
  const date = new Date()
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 10)
}

export function listSlots(placeId: string, offeringId: string, date: string, signal?: AbortSignal): Promise<string[]> {
  const start = new Date(`${date}T00:00:00`)
  const end = new Date(start)
  end.setDate(end.getDate() + 1)
  const query = new URLSearchParams({ offeringId, from: start.toISOString(), to: end.toISOString() })
  return request<string[]>(`/places/${placeId}/availability?${query}`, { signal })
}

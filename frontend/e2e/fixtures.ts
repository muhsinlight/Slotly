import { expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

export const api = 'http://127.0.0.1:8081/api'
const password = 'BrowserTest-2026!'

export async function post(request: APIRequestContext, path: string, data: unknown, token = '') {
  const response = await request.post(api + path, {
    data,
    headers: token ? { Authorization: 'Bearer ' + token } : {},
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

export async function account(request: APIRequestContext, role: 'OWNER' | 'CUSTOMER') {
  const username = role.toLowerCase() + '-' + randomUUID().slice(0, 12)
  await post(request, '/auth/register', { username, password, firstName: 'Browser', lastName: 'Test', role })
  const login = await post(request, '/auth/login', { username, password })
  return { username, token: login.accessToken as string }
}

export async function fixture(request: APIRequestContext) {
  const owner = await account(request, 'OWNER')
  const customer = await account(request, 'CUSTOMER')
  const other = await account(request, 'CUSTOMER')
  const name = 'Test Stüdyo ' + randomUUID().slice(0, 8)
  const place = await post(request, '/owner/places', {
    name, description: 'Yalnız tarayıcı testi', address: 'Örnek Mahallesi', capacity: 1, active: true,
  }, owner.token)
  const offering = await post(request, '/owner/places/' + place.id + '/offerings', {
    name: 'Bakım', price: 250, description: null, durationMinutes: 30, kind: 'SERVICE', active: true,
  }, owner.token)
  const date = new Date(Date.now() + 2 * 86_400_000).toISOString().slice(0, 10)
  const days = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
  await post(request, '/owner/places/' + place.id + '/business-hours', {
    dayOfWeek: days[new Date(date + 'T12:00:00Z').getUTCDay()], opensAt: '09:00', closesAt: '18:00',
  }, owner.token)
  return { owner, customer, other, place, offering, date, startsAt: date + 'T09:00:00Z' }
}

export async function login(page: Page, username: string, loginPassword = password) {
  await page.getByLabel('Kullanıcı adı', { exact: true }).fill(username)
  await page.getByLabel('Parola', { exact: true }).fill(loginPassword)
  await page.getByRole('button', { name: /^Giriş yap/ }).click()
}

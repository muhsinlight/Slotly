import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const api = 'http://127.0.0.1:8081/api'
const password = 'BrowserTest-2026!'

async function post(request: APIRequestContext, path: string, data: unknown, token = '') {
  const response = await request.post(api + path, {
    data,
    headers: token ? { Authorization: 'Bearer ' + token } : {},
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

async function account(request: APIRequestContext, role: 'OWNER' | 'CUSTOMER') {
  const username = role.toLowerCase() + '-' + randomUUID().slice(0, 12)
  await post(request, '/auth/register', { username, password, firstName: 'Browser', lastName: 'Test', role })
  const login = await post(request, '/auth/login', { username, password })
  return { username, token: login.accessToken as string }
}

async function fixture(request: APIRequestContext) {
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

async function login(page: Page, username: string) {
  await page.getByLabel('Kullanıcı adı', { exact: true }).fill(username)
  await page.getByLabel('Parola', { exact: true }).fill(password)
  await page.getByRole('button', { name: /^Giriş yap/ }).click()
}

test('customer finds a place, keeps their choice through login, books and cancels on mobile', async ({ page, request }) => {
  const data = await fixture(request)
  await page.goto('/')
  await page.getByRole('searchbox', { name: 'Mekân ara' }).fill(data.place.name)
  await page.getByRole('link', { name: new RegExp(data.place.name) }).click()
  await page.getByLabel('Tarih', { exact: true }).fill(data.date)
  await page.getByRole('button', { name: '12:00', exact: true }).click()
  await page.getByRole('button', { name: 'Giriş yap ve devam et' }).click()
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  await login(page, data.customer.username)
  await expect(page).toHaveURL(new RegExp('/places/' + data.place.id))
  await expect(page.getByRole('button', { name: '12:00', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByLabel('Tarih', { exact: true })).toHaveValue(data.date)
  await page.getByRole('button', { name: 'Randevuyu onayla' }).click()
  await expect(page).toHaveURL(/\/appointments/)
  await expect(page.getByRole('article')).toContainText(data.place.name)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'İptal et', exact: true }).click()
  await page.getByRole('tab', { name: 'İptal edilen' }).click()
  await expect(page.getByRole('article')).toContainText('İptal edildi')
})

test('a competing booking shows a useful error and the customer can join and accept the waitlist', async ({ page, request }) => {
  const data = await fixture(request)
  await page.goto('/login?returnTo=' + encodeURIComponent('/places/' + data.place.id + '?date=' + data.date))
  await login(page, data.customer.username)
  await page.getByRole('button', { name: '12:00', exact: true }).click()
  const appointment = await post(request, '/appointments', {
    placeId: data.place.id, offeringId: data.offering.id, startsAt: data.startsAt,
  }, data.other.token)
  await page.getByRole('button', { name: 'Randevuyu onayla' }).click()
  await expect(page.getByRole('alert')).toContainText('Bu saat az önce doldu')
  await page.getByLabel('Beklemek istediğin saat').fill('12:00')
  await page.getByRole('button', { name: 'Bekleme listesine katıl', exact: true }).click()
  await expect(page).toHaveURL(/\/waitlist/)
  await expect(page.getByRole('article')).toContainText('Sırada')
  await page.getByRole('link', { name: 'Mekânlar', exact: true }).click()
  const cancelled = await request.delete(api + '/appointments/' + appointment.id, {
    headers: { Authorization: 'Bearer ' + data.other.token },
  })
  expect(cancelled.status()).toBe(204)
  await expect(page.getByRole('status')).toContainText('yeni randevu teklifin var', { timeout: 35_000 })
  await page.getByRole('link', { name: 'Teklifi incele →' }).click()
  await expect(page.getByRole('button', { name: 'Kabul et', exact: true })).toBeEnabled({ timeout: 35_000 })
  await page.getByRole('button', { name: 'Kabul et', exact: true }).click()
  await page.getByRole('link', { name: 'Randevuyu gör →' }).click()
  await expect(page.getByRole('article')).toContainText(data.place.name)
  const response = await request.get(api + '/appointments/me', {
    headers: { Authorization: 'Bearer ' + data.customer.token },
  })
  expect((await response.json()).data).toHaveLength(1)
})

test('API 401 clears the session and preserves the booking choice', async ({ page, request }) => {
  const data = await fixture(request)
  await page.goto('/login?returnTo=' + encodeURIComponent('/places/' + data.place.id + '?date=' + data.date))
  await login(page, data.customer.username)
  await page.getByRole('button', { name: '12:00', exact: true }).click()
  await page.route('**/api/appointments', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"Expired"}' })
  })
  await page.getByRole('button', { name: 'Randevuyu onayla' }).click()
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  expect(await page.evaluate(() => localStorage.getItem('slotly-token'))).toBeNull()
  await page.unroute('**/api/appointments')
  await login(page, data.customer.username)
  await expect(page.getByRole('button', { name: '12:00', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

import { test, expect } from '@playwright/test'
import { api, fixture, login, post } from './fixtures.js'

test.use({ timezoneId: 'America/Los_Angeles' })

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

test('customer changes an upcoming appointment without losing its identity', async ({ page, request }) => {
  const data = await fixture(request)
  const appointment = await post(request, '/appointments', {
    placeId: data.place.id, offeringId: data.offering.id, startsAt: data.startsAt,
  }, data.customer.token)

  await page.goto('/login?returnTo=' + encodeURIComponent('/appointments'))
  await login(page, data.customer.username)
  await page.getByRole('button', { name: 'Saat değiştir' }).click()
  await page.getByLabel('Yeni tarih').fill(data.date)
  await page.getByRole('combobox', { name: 'Yeni saat' }).selectOption(data.date + 'T09:30:00Z')
  await page.getByRole('button', { name: 'Yeni saati onayla' }).click()
  await expect(page.getByRole('combobox', { name: 'Yeni saat' })).toHaveCount(0)

  const response = await request.get(api + '/appointments/me', {
    headers: { Authorization: 'Bearer ' + data.customer.token },
  })
  const appointments = (await response.json()).data
  expect(appointments).toHaveLength(1)
  expect(appointments[0]).toMatchObject({ id: appointment.id, startsAt: data.date + 'T09:30:00Z' })
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

test('owner manages closed and special dates while existing appointments stay protected', async ({ page, request }) => {
  const data = await fixture(request)
  await page.goto('/login?returnTo=' + encodeURIComponent('/owner'))
  await login(page, data.owner.username)
  const calendar = page.getByRole('region', { name: 'Özel günler', exact: true })
  await calendar.getByLabel('Özel gün tarihi').fill(data.date)
  await calendar.getByRole('button', { name: 'Özel günü kaydet', exact: true }).click()
  await expect(calendar.getByRole('article')).toContainText('Kapalı')
  const availability = () => request.get(api + '/places/' + data.place.id + '/availability', {
    params: { offeringId: data.offering.id, from: data.date + 'T00:00:00Z', to: data.date + 'T23:59:59Z' },
  })
  expect((await (await availability()).json()).data).toHaveLength(0)

  await calendar.getByLabel('Bu gün kapalı').uncheck()
  await calendar.getByLabel('Özel açılış').fill('12:00')
  await calendar.getByLabel('Özel kapanış').fill('13:00')
  await calendar.getByRole('button', { name: 'Özel günü kaydet', exact: true }).click()
  await expect(calendar.getByRole('article')).toContainText('12:00 – 13:00')
  expect((await (await availability()).json()).data).toHaveLength(2)
  await post(request, '/appointments', {
    placeId: data.place.id, offeringId: data.offering.id, startsAt: data.startsAt,
  }, data.customer.token)
  await calendar.getByLabel('Bu gün kapalı').check()
  await calendar.getByRole('button', { name: 'Özel günü kaydet', exact: true }).click()
  await expect(calendar.getByRole('alert')).toContainText('mevcut randevu')
  await expect(calendar.getByRole('article')).toContainText('12:00 – 13:00')

  page.once('dialog', (dialog) => dialog.accept())
  await calendar.getByRole('button', { name: 'Kaldır', exact: true }).click()
  await expect(calendar.getByRole('status')).toContainText('haftalık çalışma saatleri geçerli')
  expect((await (await availability()).json()).data.length).toBeGreaterThan(2)
})

test('owner changing places ignores a late response from the previous selection', async ({ page, request }) => {
  const data = await fixture(request)
  const second = await post(request, '/owner/places', {
    name: 'Second studio', address: 'Second address', capacity: 1, active: true,
  }, data.owner.token)
  let releaseResponse!: () => void
  let markStarted!: () => void
  let markFinished!: () => void
  const delayed = new Promise<void>((resolve) => { releaseResponse = resolve })
  const started = new Promise<void>((resolve) => { markStarted = resolve })
  const finished = new Promise<void>((resolve) => { markFinished = resolve })
  await page.goto('/login?returnTo=' + encodeURIComponent('/owner'))
  await login(page, data.owner.username)
  const selector = page.getByRole('combobox', { name: 'Mekân', exact: true })
  await selector.selectOption(second.id)
  await expect(page.getByLabel('Mekân adı', { exact: true })).toHaveValue(second.name)
  await page.route('**/owner/places/' + data.place.id + '/offerings', async (route) => {
    const response = await route.fetch()
    markStarted()
    await delayed
    await route.fulfill({ response })
    markFinished()
  })
  await selector.selectOption(data.place.id)
  await started
  const save = page.getByRole('button', { name: 'Mekân bilgilerini kaydet' })
  await expect(save).toBeDisabled()
  await expect(page.getByLabel('Adres', { exact: true })).toBeDisabled()
  await page.locator('#owner-settings form').dispatchEvent('submit')
  await selector.selectOption(second.id)
  await expect(page.getByLabel('Mekân adı', { exact: true })).toHaveValue(second.name)
  releaseResponse()
  await finished
  // Subsequent rendering must still show the currently selected place's data.
  await expect(page.getByLabel('Mekân adı', { exact: true })).toHaveValue(second.name)
  await expect(page.getByText('Bakım', { exact: true })).toHaveCount(0)
  await page.getByLabel('Adres', { exact: true }).fill('Yeni şube adresi')
  await save.click()
  await expect(page.getByRole('status')).toContainText('Mekân bilgileri güncellendi.')
  const places = await request.get(api + '/owner/places', { headers: { Authorization: 'Bearer ' + data.owner.token } })
  expect((await places.json()).data).toEqual(expect.arrayContaining([
    expect.objectContaining({ id: data.place.id, name: data.place.name, address: data.place.address }),
    expect.objectContaining({ id: second.id, name: second.name, address: 'Yeni şube adresi' }),
  ]))
})

test('catalog lists active products with prices and only services can be booked', async ({ page, request }) => {
  const data = await fixture(request)
  await post(request, `/owner/places/${data.place.id}/offerings`, {
    name: 'Bakım yağı', price: 125, description: null, durationMinutes: null, kind: 'PRODUCT', active: true,
  }, data.owner.token)
  await post(request, `/owner/places/${data.place.id}/offerings`, {
    name: 'Gizli ürün', price: 50, description: null, durationMinutes: null, kind: 'PRODUCT', active: false,
  }, data.owner.token)
  await page.goto('/places/' + data.place.id)
  const catalog = page.getByRole('region', { name: 'Hizmet ve ürün fiyatları' })
  const product = catalog.getByRole('article').filter({ hasText: 'Bakım yağı' })
  await expect(product).toContainText('125')
  await expect(product.getByRole('button')).toHaveCount(0)
  await expect(catalog.getByRole('article')).toHaveCount(2)
  const options = page.getByRole('combobox', { name: 'Hizmet', exact: true }).locator('option')
  await expect(options).toHaveCount(1)
  await expect(options).toHaveText(/Bakım · 30 dk/)
  await expect(page.getByText('Gizli ürün')).toHaveCount(0)
})

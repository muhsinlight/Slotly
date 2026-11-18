import { test, expect } from '@playwright/test'
import postgres from 'postgres'
import { api, fixture, login, post } from './fixtures.js'

test.use({ timezoneId: 'America/Los_Angeles' })

test('owner saves both outcomes from a device outside the business time zone', async ({ page, request }) => {
  const data = await fixture(request)
  const booking = { placeId: data.place.id, offeringId: data.offering.id, startsAt: data.startsAt }
  const first = await post(request, '/appointments', booking, data.customer.token)
  const second = await post(request, '/appointments', { ...booking, startsAt: data.date + 'T09:30:00Z' }, data.other.token)

  // Public booking accepts future times. Age only these two fixtures so the real API allows an outcome.
  const jdbcUrl = process.env.E2E_DATABASE_URL || 'jdbc:postgresql://127.0.0.1:5434/slotly_browser_test'
  const databaseUrl = new URL(jdbcUrl.replace(/^jdbc:/, ''))
  expect(databaseUrl.pathname).toMatch(/^\/[a-z0-9_]+_test$/i)
  const sql = postgres(databaseUrl.toString(), {
    username: process.env.E2E_DATABASE_USERNAME || 'slotly',
    password: process.env.E2E_DATABASE_PASSWORD || 'slotly',
    max: 1,
  })
  try {
    const changed = await sql`
      UPDATE appointments SET starts_at = starts_at - interval '7 days', ends_at = ends_at - interval '7 days'
      WHERE place_id = ${data.place.id} AND id IN (${first.id}, ${second.id})
      RETURNING id
    `
    expect(changed).toHaveLength(2)
  } finally {
    await sql.end()
  }

  await page.goto('/login')
  await login(page, data.owner.username)
  const date = new Date(Date.parse(data.startsAt) - 7 * 86_400_000).toISOString().slice(0, 10)
  const from = new Date(date + 'T00:00:00+03:00')
  const calendarRequest = page.waitForRequest(req => {
    const url = new URL(req.url())
    return url.pathname === '/api/owner/appointments' && Date.parse(url.searchParams.get('from') || '') === from.getTime()
  })
  await page.getByLabel('Tarih', { exact: true }).fill(date)
  const params = new URL((await calendarRequest).url()).searchParams
  expect(Date.parse(params.get('to') || '')).toBe(from.getTime() + 86_400_000)

  const noon = page.getByRole('article').filter({ hasText: '12:00 · Browser Test' })
  const later = page.getByRole('article').filter({ hasText: '12:30 · Browser Test' })
  page.on('dialog', dialog => dialog.accept())
  await noon.getByRole('button', { name: 'Tamamlandı' }).click()
  await expect(noon.locator('.status')).toHaveText('Tamamlandı')
  await later.getByRole('button', { name: 'Gelmedi' }).click()
  await expect(later.locator('.status')).toHaveText('Gelmedi')
  const saved = await request.get(api + '/owner/appointments?' + params, {
    headers: { Authorization: 'Bearer ' + data.owner.token },
  })
  expect((await saved.json()).data).toEqual([
    expect.objectContaining({ id: first.id, status: 'COMPLETED' }),
    expect.objectContaining({ id: second.id, status: 'NO_SHOW' }),
  ])
})

test('admin login opens the overview and searches across appointment pages', async ({ page, request }) => {
  const data = await fixture(request)
  const opens = Date.parse(data.date + 'T06:00:00Z')
  for (let i = 0; i < 21; i++) {
    const week = Math.floor(i / 18)
    const startsAt = new Date(opens + week * 7 * 86_400_000 + (i % 18) * 30 * 60_000).toISOString()
    await post(request, '/appointments', { placeId: data.place.id, offeringId: data.offering.id, startsAt }, data.customer.token)
  }
  await page.goto('/login')
  await login(page, 'browser-admin', 'BrowserAdmin2026!')
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('link', { name: 'Yönetim paneli' })).toBeVisible()
  const search = page.getByRole('searchbox', { name: 'İşletme, müşteri veya hizmet ara' })
  const filtered = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === '/api/admin/overview' && url.searchParams.get('q') === data.place.name
  })
  await search.fill(data.place.name)
  expect((await filtered).ok()).toBeTruthy()
  const rows = page.locator('.admin-record')
  await expect(rows).toHaveCount(20)
  await expect(rows.first()).toContainText(data.place.name)
  const navigation = page.getByRole('navigation', { name: 'Kayıt sayfaları' })
  await navigation.getByRole('button', { name: 'Sonraki' }).click()
  await expect(navigation).toContainText('Sayfa 2 / 2')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText(data.place.name)
  await page.getByRole('tab', { name: 'Bekleme listesi' }).click()
  await expect(page.getByText('Bekleme kaydı bulunamadı.')).toBeVisible()
})

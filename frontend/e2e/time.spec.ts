import { test, expect } from '@playwright/test'
import { calendarRange, possibleStarts, timeFormatter } from '../src/lib/businessTime.js'
import { account } from './fixtures.js'

test('business dates cover short and long days and distinguish the two fall-back starts', () => {
  const zone = 'America/New_York'
  expect(calendarRange('2026-03-08', zone)).toEqual({ from: '2026-03-08T05:00:00Z', to: '2026-03-09T04:00:00Z' })
  expect(calendarRange('2026-11-01', zone, 'week')).toEqual({ from: '2026-10-26T04:00:00Z', to: '2026-11-02T05:00:00Z' })
  expect(possibleStarts('2026-03-08', '02:30', zone)).toHaveLength(0)
  const choices = possibleStarts('2026-11-01', '01:30', zone)
  expect(choices).toEqual([
    { instant: '2026-11-01T05:30:00Z', offset: '-04:00' },
    { instant: '2026-11-01T06:30:00Z', offset: '-05:00' },
  ])
  const format = timeFormatter(choices[0].instant, zone)
  expect(format.format(Date.parse(choices[0].instant))).not.toEqual(format.format(Date.parse(choices[1].instant)))
})

test('acceptance and waiting end exactly when the appointment starts', async ({ page, request }) => {
  const customer = await account(request, 'CUSTOMER')
  const now = Date.now()
  await page.clock.install({ time: new Date(now - 1000) })
  await page.clock.pauseAt(new Date(now))
  await page.addInitScript(token => localStorage.setItem('slotly-token', token), customer.token)
  const startsAt = new Date(now + 5_000).toISOString()
  const entry = {
    placeId: 'place', placeName: 'Stüdyo', offeringId: 'service', offeringName: 'Bakım',
    startsAt, endsAt: new Date(now + 30 * 60_000).toISOString(),
  }
  await page.route('**/api/waitlist/me', route => route.fulfill({ json: { data: [
    { ...entry, id: 'offered', status: 'OFFERED', offerExpiresAt: new Date(now + 20 * 60_000).toISOString() },
    { ...entry, id: 'waiting', status: 'WAITING', offerExpiresAt: null },
  ] } }))
  await page.goto('/waitlist')
  await expect(page.getByRole('button', { name: 'Kabul et' })).toBeEnabled()
  await expect(page.getByText(/Kalan süre 00:05/)).toBeVisible()
  await page.clock.fastForward(4999)
  await expect(page.getByRole('button', { name: 'Kabul et' })).toBeEnabled()
  await page.clock.fastForward(1)
  await expect(page.getByText('Süresi doldu', { exact: true })).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'Kabul et' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Listeden ayrıl' })).toHaveCount(0)
})

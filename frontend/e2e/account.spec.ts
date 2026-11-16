import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test('customer manages email verification from account settings', async ({ page, request }) => {
  const username = 'email-' + randomUUID().slice(0, 8)
  const password = 'EmailBrowser2026!'
  const created = await request.post('http://127.0.0.1:8081/api/auth/register', {
    data: { username, password, firstName: 'Email', lastName: 'Test', role: 'CUSTOMER' },
  })
  expect(created.status()).toBe(201)
  await page.goto('/login')
  await page.getByLabel('Kullanıcı adı', { exact: true }).fill(username)
  await page.getByLabel('Parola', { exact: true }).fill(password)
  await page.getByRole('button', { name: /^Giriş yap/ }).click()
  await page.getByRole('link', { name: 'Hesap ayarları' }).click()
  await expect(page.getByRole('heading', { name: 'Hesap ayarları' })).toBeVisible()
  await page.getByLabel('E-posta', { exact: true }).fill(username + '@example.test')
  await page.getByRole('button', { name: 'Kaydet ve doğrulama bağlantısı iste' }).click()
  await expect(page.getByRole('status')).toContainText('gönderim kuyruğuna alındı')
  await page.reload()
  await expect(page.getByLabel('E-posta', { exact: true })).toHaveValue(username + '@example.test')
  await page.goto('/verify-email#token=invalid')
  await page.getByRole('button', { name: 'Adresimi doğrula' }).click()
  await expect(page.getByRole('alert')).toContainText('geçersiz')
})

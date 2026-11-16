import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test('password recovery keeps account existence private and rejects invalid links', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('link', { name: 'Parolamı unuttum' }).click()
  await page.getByLabel('E-posta', { exact: true }).fill('unknown-' + randomUUID().slice(0, 8) + '@example.test')
  await page.getByRole('button', { name: 'Sıfırlama bağlantısı iste' }).click()
  await expect(page.getByRole('status')).toContainText('Adres doğrulanmış bir hesaba aitse')
  await page.goto('/reset-password#token=invalid')
  await page.getByLabel('Yeni parola', { exact: true }).fill('NewPassword2026!')
  await page.getByLabel('Yeni parolayı tekrar yaz', { exact: true }).fill('DifferentPassword2026!')
  await page.getByRole('button', { name: 'Parolayı değiştir' }).click()
  await expect(page.getByRole('alert')).toContainText('eşleşmiyor')
  await page.getByLabel('Yeni parolayı tekrar yaz', { exact: true }).fill('NewPassword2026!')
  await page.getByRole('button', { name: 'Parolayı değiştir' }).click()
  await expect(page.getByRole('alert')).toContainText('geçersiz')
})

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
  await expect(page.getByRole('checkbox', { name: 'Boşalan saat tekliflerini e-posta ile al' })).toBeDisabled()
  await expect(page.getByRole('heading', { name: 'Son bildirimler' })).toBeVisible()
  await page.getByLabel('E-posta', { exact: true }).fill(username + '@example.test')
  await page.getByRole('button', { name: 'Kaydet ve doğrulama bağlantısı iste' }).click()
  await expect(page.getByRole('status')).toContainText('gönderim kuyruğuna alındı')
  await page.reload()
  await expect(page.getByLabel('E-posta', { exact: true })).toHaveValue(username + '@example.test')
  await page.goto('/verify-email#token=invalid')
  await page.getByRole('button', { name: 'Adresimi doğrula' }).click()
  await expect(page.getByRole('alert')).toContainText('geçersiz')
})

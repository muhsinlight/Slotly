import { defineConfig, devices } from '@playwright/test'

const databaseUrl = process.env.E2E_DATABASE_URL || 'jdbc:postgresql://127.0.0.1:5434/slotly_browser_test'
if (!/\/[a-z0-9_]+_test(?:\?|$)/i.test(databaseUrl)) {
  throw new Error('Browser tests require a dedicated database ending in _test.')
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    timezoneId: 'Europe/Istanbul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'mobile-chromium', use: { ...devices['Pixel 7'] } }],
  webServer: [
    {
      command: 'java -jar ../backend/target/slotly-api-0.0.1-SNAPSHOT.jar',
      url: 'http://127.0.0.1:8081/actuator/health',
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        SPRING_PROFILES_ACTIVE: 'e2e',
        SERVER_PORT: '8081',
        DATABASE_URL: databaseUrl,
        DATABASE_USERNAME: process.env.E2E_DATABASE_USERNAME || 'slotly',
        DATABASE_PASSWORD: process.env.E2E_DATABASE_PASSWORD || 'slotly',
        JWT_SECRET: 'isolated-browser-tests-only-signing-key-32-bytes',
        CORS_ALLOWED_ORIGINS: 'http://127.0.0.1:4173',
        BUSINESS_TIME_ZONE: 'Europe/Istanbul',
        LOGIN_RATE_LIMIT_ATTEMPTS: '1000',
        SLOTLY_ADMIN_USERNAME: 'browser-admin',
        SLOTLY_ADMIN_PASSWORD: 'BrowserAdmin2026!',
      },
    },
    {
      command: 'pnpm run dev --host 127.0.0.1 --port 4173 --strictPort',
      url: 'http://127.0.0.1:4173',
      reuseExistingServer: false,
      env: { VITE_API_BASE_URL: 'http://127.0.0.1:8081' },
    },
  ],
})

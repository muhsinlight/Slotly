# Slotly web arayüzü

Vue 3, TypeScript ve Vite ile geliştirilmiştir. Genel kurulum için [ana README](../README.md), testler için [geliştirme rehberi](../docs/DEVELOPMENT.md).

```sh
pnpm install --frozen-lockfile
pnpm run dev
```

API varsayılan olarak `http://localhost:8080` adresinde beklenir. Farklı adres için [.env.example](.env.example) içindeki `VITE_API_BASE_URL` değerini kullanın; `/api` uygulama tarafından eklenir.

- `pnpm run build`: TypeScript kontrolü ve üretim derlemesi.
- `pnpm run preview`: Üretim derlemesini yerelde önizleme.
- `pnpm run test:e2e`: Hazır test ortamına karşı Playwright testleri. Ortamı da başlatmak için `scripts/test-browser.ps1` betiğini kullanın.

# Slotly web arayüzü

Vue 3, TypeScript ve Vite ile geliştirilmiştir. Genel kurulum için [ana README](../README.md), testler için [geliştirme rehberi](../docs/DEVELOPMENT.md).

```sh
npm ci
npm run dev
```

API varsayılan olarak `http://localhost:8080` adresinde beklenir. Farklı adres için [.env.example](.env.example) içindeki `VITE_API_BASE_URL` değerini kullanın; `/api` uygulama tarafından eklenir.

- `npm run build`: TypeScript kontrolü ve üretim derlemesi.
- `npm run preview`: Üretim derlemesini yerelde önizleme.
- `npm run test:e2e`: Hazır test ortamına karşı Playwright testleri. Ortamı da başlatmak için kökteki `test-browser.ps1` betiğini kullanın.

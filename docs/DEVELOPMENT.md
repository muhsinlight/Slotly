# Geliştirme ve doğrulama

[← Slotly](../README.md)

## Geliştirme kontrolleri

Windows'ta GitHub'a göndermeden önce CI kontrollerinin tamamını proje kökünden çalıştırın (Docker Desktop, Java 25 ve Node.js 24 gerekir):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\check.ps1
```

Komut backend `verify`, yedek geri yükleme, `npm ci`, frontend tür kontrolü/derleme ve Chromium tarayıcı testlerini sırayla çalıştırır; ilk hatada durur. Test veritabanları geçicidir. Chromium ilk kullanımda indirilir.

`check.ps1` yerel bir dosyadır; `.gitignore` kapsamındadır ve GitHub'a gönderilmez. Yeni bir klonda kullanmak için önce dosyayı proje köküne ayrıca kopyalayın. Her `git push` öncesinde otomatik çalışması için her yeni klonda bir kez kurun:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\install-hooks.ps1
```

Kanca hata olduğunda push'u engeller. Kontrollerin gönderilen commit'i doğrulaması için çalışma ağacının temiz olmasını ve gönderilen commit'in mevcut `HEAD` olmasını ister; yalnız dal silen push'larda test çalıştırmaz. Git kancası yereldir ve atlanabilir; GitHub CI kontrolleri etkin kalır ve Linux ortamındaki farkları da yakalar.

Backend testleri:

Backend testleri geçici PostgreSQL 17 konteynerinde çalışır. Ayrı test veritabanı, 5433 portu ve geçici disk kullanılır; geliştirme veritabanına dokunulmaz.

Testler `integration-test` profilini ve `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` değişkenlerini kullanır. Normal uygulamanın `DATABASE_URL` değeri test bağlantısını değiştirmez. Doğrudan Maven test komutu da varsayılan olarak 5433 üzerindeki `slotly_test` veritabanını bekler.

```powershell
cd backend
.\test-with-postgres.ps1
```

Frontend TypeScript kontrolü ve üretim derlemesi:

```powershell
cd frontend
npm run build
```

Mobil müşteri akışının tarayıcı testi (gerçek API, ayrı ve geçici PostgreSQL):

```powershell
cd frontend
npm ci
npx playwright install chromium
cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-browser.ps1
```

Tarayıcı ortamı veritabanı için 5434, API için 8081 ve arayüz için 4173 portlarını kullanır. Testler seçimle girişe dönüşü, rezervasyon/iptali, saat çakışmasını, bekleme listesini ve 401 sonrası dönüşü kapsar. CI aynı akışları PostgreSQL servisiyle çalıştırır; başarısızlık raporunu artifact olarak saklar.

`.github/workflows/ci.yml`, tüm dallara push ve `main` hedefli pull requestlerde backend doğrulamasını temiz PostgreSQL servisiyle, frontend tür kontrolü ve üretim derlemesini çalıştırır. Görev dalları yalnız kontroller geçtikten sonra `main` ile birleştirilir.

### Yedek ve geri yükleme

Yerel backend test betiği ve CI, yalnızca geçici test veritabanında `pg_dump`/`pg_restore` denemesi yapar. Kopya `slotly_restore_test` veritabanında altı uygulama tablosunun kayıt sayıları ve içerik checksum'ları karşılaştırılır; ardından kopya kaldırılır. Bu kontrol canlı yedeklerin saklanması ve gerçek ortamda geri dönüş denemesinin yerine geçmez.

Canlı PostgreSQL veritabanını düzenli yedekleyin, şifreli kopyayı veritabanı sunucusunun dışında tutun ve pilot öncesinde ayrı bir veritabanına geri yükleyerek deneyin. Geri yüklemeyi canlı veritabanının üzerine test etmeyin.

```powershell
$env:PGHOST = 'localhost'
$env:PGPORT = '5432'
$env:PGUSER = 'slotly_backup'
$env:PGPASSWORD = '<secret-from-your-secret-store>'
$env:PGDATABASE = 'slotly'
pg_dump --format=custom --no-owner --file slotly-backup.dump

$env:PGDATABASE = 'slotly_restore_check'
createdb --owner=$env:PGUSER $env:PGDATABASE
pg_restore --no-owner --dbname=$env:PGDATABASE slotly-backup.dump
```

Yedek saklama ve şifrelemeyi barındırma ortamının secret/yedek hizmetinde ayarlayın. Yalnızca dump alabilmek geri yüklemenin çalıştığını kanıtlamaz.

API uçlarının CLI ile denenmiş akışları için [tarihli CLI test raporu](testing/API-CLI-TEST-REPORT.md) dosyasına bakın.

## Durdurma ve yerel veriler

API ve Vite süreçlerini çalıştıkları terminallerde `Ctrl+C` ile durdurun. PostgreSQL'i proje kökünden durdurmak için:

```powershell
docker compose down
```

Bu komut veritabanı volume'unu korur. Yerel veritabanını ve içindeki verileri de silmek için `docker compose down -v` kullanılır.


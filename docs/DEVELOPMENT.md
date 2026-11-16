# Geliştirme ve doğrulama

[← Slotly](../README.md)

## Servis sorumlulukları ve pilot sınırları

Mekân/hizmet/haftalık saat yönetimi `PlaceService`, özel günler `BusinessCalendarService`, uygun saatler `AvailabilityService`, kapasite hesabı `BookingCapacityService`, randevu işlemleri `BookingService` ve FIFO/teklif akışı `WaitlistService` tarafından yönetilir. Controller'lar kullandıkları dar servis sözleşmesine bağlanır.

Rezervasyon, teklif ve kapasite değişiklikleri aynı mekân satırını kilitler. Kontrol ve güncelleme aynı transaction içinde kalır; yalnız `@Transactional` eklemek eşzamanlı istekleri tek başına sıralamaz. Kapasite, devam eden/gelecek onaylı randevular ile aktif tekliflerin en yüksek eşzamanlı yükünden düşük olamaz. Ardışık kayıtların aynı ana denk gelen bitiş ve başlangıcı iki kapasite olarak sayılmaz.

Pilot tek backend örneğine göre hazırlanmıştır. Giriş/kurtarma istek sınırları ve SSE abonelikleri süreç belleğindedir. Yeniden başlatmada hız sınırları sıfırlanır; birden fazla örnekte sınırlar paylaşılmaz ve teklif olayları diğer örneğin SSE bağlantısına doğrudan ulaşmaz. Frontend yedek yenilemesi bildirim gecikmesini azaltır. Yatay ölçeklemeden önce ortak hız sınırı ve örnekler arası olay dağıtımı gerekir. JWT'nin localStorage içinde saklanması ve oturum yenileme yaklaşımı pilot öncesinde değerlendirilecek açık karardır.

## Geliştirme kontrolleri

Test, derleme ve push çıktıları varsayılan olarak terminalde tutulur. Dosyaya kayıt gerektiğinde proje kökünde log oluşturmayın; Git dışında tutulan `tmp/verification/` dizininde işlem başına sabit ad kullanın: `backend.log`, `build.log`, `browser.log`, `api.log` veya `push.log`. Her çalıştırmada aynı dosyanın üzerine yazın; özellik veya tarih içeren yeni adlarla dosya biriktirmeyin. Bu geçici çıktılar, aşağıda açıklanan backend çalışma loglarından ayrıdır.

Windows'ta GitHub'a göndermeden önce CI kontrollerinin tamamını proje kökünden çalıştırın (Docker Desktop, Java 25 ve Node.js 24 gerekir):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check.ps1
```

Komut backend `verify`, yedek geri yükleme, `npm ci`, frontend tür kontrolü/derleme ve Chromium tarayıcı testlerini sırayla çalıştırır; ilk hatada durur. Test veritabanları geçicidir. Chromium ilk kullanımda indirilir.

`scripts/check.ps1` yerel bir dosyadır; `.gitignore` kapsamındadır ve GitHub'a gönderilmez. Yeni bir klonda kullanmak için önce dosyayı `scripts/` klasörüne ayrıca kopyalayın. Her `git push` öncesinde otomatik çalışması için her yeni klonda bir kez kurun:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-hooks.ps1
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
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-browser.ps1
```

Tarayıcı ortamı veritabanı için 5434, API için 8081 ve arayüz için 4173 portlarını kullanır. Testler seçimle girişe dönüşü, rezervasyon/iptali, saat çakışmasını, bekleme listesini ve 401 sonrası dönüşü kapsar. CI aynı akışları PostgreSQL servisiyle çalıştırır; başarısızlık raporunu artifact olarak saklar.

`.github/workflows/ci.yml`, tüm dallara push ve `main` hedefli pull requestlerde backend doğrulamasını temiz PostgreSQL servisiyle, frontend tür kontrolü ve üretim derlemesini çalıştırır. Görev dalları yalnız kontroller geçtikten sonra `main` ile birleştirilir.

### Yedek ve geri yükleme

Yerel backend test betiği ve CI, yalnızca geçici test veritabanında `pg_dump`/`pg_restore` denemesi yapar. Kopya `slotly_restore_test` veritabanında `backend/verify-restore.mjs` listesindeki tabloların tamamının kayıt sayıları ve içerik checksum'ları karşılaştırılır; ardından kopya kaldırılır. Bu kontrol canlı yedeklerin saklanması ve gerçek ortamda geri dönüş denemesinin yerine geçmez.

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


## Backend logları

Konsol kısa hata izi gösterir; tam stack trace çalışma dizinine göre logs/slotly.log dosyasına yazılır. Beklenmeyen API ve veritabanı hatalarında köşeli parantezdeki UUID ile dosyada arama yapılabilir. API yanıt sözleşmesi değişmez. Başlangıç hataları da aynı konsol/dosya düzenini kullanır.

Dosyalar günlük veya 10 MB olduğunda döner; 14 günlük geçmiş ve toplam 200 MB sınırı uygulanır. Dizin slotly.logging.directory ile değiştirilebilir. Paket ayrıntısı gerektiğinde logging.level.org.springframework=DEBUG gibi bir ayarla geçici açılabilir. Log dosyaları teknik hata mesajları içerebilir; erişimi sınırlandırın.


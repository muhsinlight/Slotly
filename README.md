# Slotly

Randevu ve otomatik bekleme listesi yönetimi. Müşteriler işletmeleri keşfeder ve hizmetler için randevu alır; işletme sahipleri mekânlarını, çalışma saatlerini ve hizmetlerini yönetir.

## Ürün akışı

- **İşletme sahibi (`OWNER`)** hesap açar, mekânını oluşturur, kapasite ve çalışma saatlerini belirler; hizmet veya ürün ekler ve kendi mekânlarının randevularını görür.
- **Müşteri (`CUSTOMER`)** açık mekânları ve hizmetleri inceler, uygun saati seçer; randevu alır, randevularını görüntüler veya iptal eder.
- İstenen saatte kapasite doluysa müşteri bekleme listesine katılır. Randevu iptal edildiğinde aynı hizmet ve saat için sıradaki kişiye FIFO ile 30 dakikalık teklif açılır. Teklif reddedilirse veya süresi dolarsa sıradaki kişiye geçilir.

## Özellikler ve temel kurallar

- Mekân, çalışma saatleri, hizmet ve ürün keşfi.
- Hizmet süresine göre uygun randevu saatlerini bulma.
- Randevu oluşturma, görüntüleme ve iptal etme.
- Mekân sahibi için mekân, hizmet/ürün, çalışma saati ve randevu yönetimi.
- Bekleme listesine katılma; teklif kabul etme, reddetme veya listeden ayrılma.
- Yalnız etkin ve pozitif süreli `SERVICE` kayıtları randevuya alınır. `PRODUCT` kayıtları keşif ve fiyat listesinde görünür, randevuya bağlanmaz.
- Uygun saatler çalışma aralığının açılışından başlayarak hizmet süresi kadar adımlarla üretilir. Arama aralığı en fazla 31 gündür.
- Kapasite, aktif randevuların zaman aralığı çakışmalarına göre hesaplanır. Eşzamanlı rezervasyonlarda kontrol ve kayıt mekân kilidi altında yapılır.
- Çalışma saatleri varsayılan olarak `Europe/Istanbul` saat diliminde yorumlanır.

## Teknoloji ve gereksinimler

- Java 25, Spring Boot 4.1.2, Maven Wrapper, Spring Security, JPA.
- PostgreSQL 17 (yerel geliştirme için Docker Compose).
- Vue 3, TypeScript ve Vite.
- Git, JDK 25, Node.js 24+ ve npm.
- PostgreSQL'i yerelde çalıştırmak için Docker Desktop ve Docker Compose.

## Kurulum ve yerelde çalıştırma

Aşağıdaki komutlar Windows PowerShell içindir. macOS/Linux karşılıkları ayrıca verilmiştir. Depo özeldir; klonlama için GitHub erişimi gerekir. Mevcut proje klasöründeyseniz klonlama adımını atlayın.

### 1. Depoyu alın

```powershell
git clone https://github.com/muhsinlight/Slotly.git
cd Slotly
```

### 2. PostgreSQL'i başlatın

Proje kökünden:

```powershell
docker compose up -d postgres
docker compose ps
```

Compose yerel geliştirme için `slotly` veritabanını, `slotly` kullanıcısını ve parolasını oluşturur; PostgreSQL 17'yi `localhost:5432` adresinde sunar. Veriler `slotly-postgres-data` adlı kalıcı volume'da tutulur. Compose yalnızca veritabanını başlatır; API ve arayüz ayrı süreçlerdir.

### 3. API'yi başlatın

Yeni bir terminal açıp proje kökünden çalıştırın:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
cd backend
./mvnw spring-boot:run
```

İlk çalıştırmada Maven bağımlılıkları indirir. API varsayılan olarak `http://localhost:8080` adresinde açılır. Sağlık kontrolü: `http://localhost:8080/actuator/health`.

### 4. Web arayüzünü başlatın

Başka bir terminalde proje kökünden çalıştırın:

```powershell
cd frontend
npm ci
npm run dev
```

Vite `http://localhost:5173` adresinde açılır. Tarayıcıda bu adresi ziyaret edin. Arayüz API'ye `http://localhost:8080/api` üzerinden bağlanır; API önce çalışıyor olmalıdır.

PostgreSQL, API ve arayüzü tek komutla başlatmak için proje kökünde çalıştırın:

```powershell
node .\dev.mjs
```

Komut PostgreSQL'i Compose ile başlatır, backend ve frontend süreçlerini aynı terminalde ayrı etiketli çıktılarla çalıştırır. Durdurmak için `Ctrl+C` kullanın; betiğin başlattığı uygulamalar durur, PostgreSQL çalışmaya devam eder. Servisler zaten açıksa yeni kopya başlatılmaz.

### 5. İlk hesabı ve mekânı kurun

1. Arayüzde **Kayıt ol** seçeneğini açın. Kullanıcı adı, en az 10 karakterli parola, ad, soyad ve rol girin.
2. İşletme kuracaksanız `OWNER`, randevu alacaksanız `CUSTOMER` seçin. Web arayüzü kayıt sonrasında otomatik olarak giriş yapar.
3. `OWNER` hesabıyla bir mekân oluşturun; kapasitesini, adresini ve çalışma saatlerini girin. Ardından randevu alınabilir bir `SERVICE` ekleyin.
4. `CUSTOMER` hesabıyla mekânı ve hizmeti seçin, tarih ve uygun saati belirleyerek randevu alın.
5. Saat doluysa istediğiniz saat için bekleme listesine katılın. Teklif geldiğinde 30 dakika içinde kabul veya reddedin.

API'yi doğrudan kullanırken kayıt ve giriş ayrı çağrılardır: `POST /api/auth/register` kullanıcı kaydeder; ardından `POST /api/auth/login` ile `accessToken` alınır.

## Yapılandırma

Backend aşağıdaki değerleri süreç ortam değişkenlerinden okur. Varsayılanlar yerel geliştirme içindir.

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `SERVER_PORT` | `8080` | API portu |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/slotly` | PostgreSQL JDBC adresi |
| `DATABASE_USERNAME` | `slotly` | Veritabanı kullanıcısı |
| `DATABASE_PASSWORD` | `slotly` | Yerel Compose parolası. Değiştirirseniz `compose.yaml` içindeki PostgreSQL parolasıyla eşleştirin. |
| `JWT_SECRET` | Yerel geliştirme anahtarı | JWT imzalama anahtarı. Yerel varsayılanı canlı ortamda kullanmayın; dış ortamda gizli, güçlü ve en az 32 baytlık bir anahtar sağlayın. |
| `BUSINESS_TIME_ZONE` | `Europe/Istanbul` | Çalışma saatlerinin yorumlandığı saat dilimi |

PowerShell'de yalnızca o terminal oturumu için ortam değişkeni ayarlama örneği:

```powershell
$env:JWT_SECRET = 'yerel-gelistirme-icin-guclu-ve-ozel-bir-anahtar'
cd backend
.\mvnw.cmd spring-boot:run
```

Hibernate, uygulama başlarken entity'lere göre veritabanı şemasını `ddl-auto=update` ile günceller. Şu an projede Flyway veya Liquibase migration akışı yoktur.

## Kimlik doğrulama ve yanıt biçimi

- Kayıt alanları: `username` (en fazla 40), `password` (10–100), `firstName` ve `lastName` (en fazla 80), `role` (`OWNER` veya `CUSTOMER`).
- Korumalı uçlara `Authorization: Bearer <accessToken>` başlığı ekleyin. JWT ömrü 3600 saniyedir.
- Oturum sunucuda tutulmaz; cookie veya token yenileme ucu yoktur. Web arayüzü token'ı `localStorage` içindeki `slotly-token` anahtarında saklar. Çıkış token'ı tarayıcıdan siler.
- JSON yanıtları `status`, `message`, `data` ve `timestamp` alanlarını içerir. `status`, HTTP durum kodudur. `204 No Content` yanıtlarında gövde yoktur.
- CORS için `http://localhost:5173` ve `http://127.0.0.1:5173` izinlidir.

## API uçları

Tüm API uçları `/api` önekini kullanır. `{id}` değerleri UUID'dir. `from`, `to` ve `startsAt` alanlarını ISO-8601 biçiminde, saat dilimi/UTC offset belirterek gönderin.

### Kayıt ve giriş

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `POST /api/auth/register` | Herkese açık | Kullanıcı kaydeder; başarılı yanıt `201` döner. |
| `POST /api/auth/login` | Herkese açık | JWT erişim token'ı döndürür. |

Kayıt gövdesi örneği:

```json
{
  "username": "ornek-musteri",
  "password": "Ornek-Sifre-2026",
  "firstName": "Ada",
  "lastName": "Yilmaz",
  "role": "CUSTOMER"
}
```

### Mekân keşfi

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `GET /api/places` | Herkese açık | Aktif mekânları listeler. |
| `GET /api/places/{id}` | Herkese açık | Aktif mekânı getirir. |
| `GET /api/places/{id}/offerings` | Herkese açık | Mekânın etkin hizmet ve ürünlerini listeler. |
| `GET /api/places/{id}/business-hours` | Herkese açık | Çalışma saatlerini listeler. |
| `GET /api/places/{id}/availability?offeringId={uuid}&from={instant}&to={instant}` | Herkese açık | Hizmet için uygun başlangıç zamanlarını döndürür; aralık en fazla 31 gündür. |

### İşletme yönetimi

Aşağıdaki uçlar yalnızca ilgili mekânın sahibi olan `OWNER` rolüyle kullanılabilir.

| Yöntem ve uç | Açıklama |
|---|---|
| `GET /api/owner/places` | Sahibin mekânlarını listeler. |
| `POST /api/owner/places` | Mekân oluşturur: `name`, `address`, `capacity`, `active`; `description` isteğe bağlıdır. |
| `PUT /api/owner/places/{id}` | Mekânı günceller. |
| `DELETE /api/owner/places/{id}` | Mekânı devre dışı bırakır. |
| `POST /api/owner/places/{placeId}/offerings` | Hizmet veya ürün ekler. `SERVICE` için pozitif `durationMinutes` gerekir; `PRODUCT` için süre gönderilmez. |
| `PUT /api/owner/places/{placeId}/offerings/{id}` | Hizmet veya ürünü günceller. |
| `DELETE /api/owner/places/{placeId}/offerings/{id}` | Hizmet veya ürünü devre dışı bırakır. |
| `POST /api/owner/places/{placeId}/business-hours` | Haftanın günü için çalışma aralığı ekler. |
| `DELETE /api/owner/places/{placeId}/business-hours/{id}` | Çalışma aralığını siler. |
| `GET /api/owner/appointments?placeId={uuid}&from={instant}&to={instant}` | Sahibin mekân ve zaman aralığına göre randevularını listeler. |

### Randevu ve bekleme listesi

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `POST /api/appointments` | `CUSTOMER` | `placeId`, `offeringId` ve gelecekteki `startsAt` ile randevu oluşturur. Bitiş hizmet süresinden hesaplanır. |
| `GET /api/appointments/me` | `CUSTOMER` | Müşterinin kendi randevularını listeler. |
| `DELETE /api/appointments/{id}` | Randevu sahibi veya mekân sahibi | Randevuyu iptal eder; iptal edilmiş randevu kapasiteyi tüketmez. |
| `GET /api/waitlist/me` | `CUSTOMER` | Müşterinin bekleme kayıtlarını ve tekliflerini listeler. |
| `GET /api/waitlist/events` | `CUSTOMER` | `text/event-stream` üzerinden yeni teklifleri iletir. Akış kesilirse istemci yeniden bağlanır ve yedek olarak kayıtları yeniler. |
| `POST /api/waitlist` | `CUSTOMER` | Kapasitesi dolu bir hizmet ve saat için bekleme kaydı açar. |
| `POST /api/waitlist/{id}/accept` | `CUSTOMER` | Aktif teklifi kabul eder ve randevu oluşturur. |
| `POST /api/waitlist/{id}/decline` | `CUSTOMER` | Aktif teklifi reddeder. |
| `DELETE /api/waitlist/{id}` | `CUSTOMER` | Bekleme listesinden ayrılır. |

### Sağlık kontrolü

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `GET /actuator/health` | Herkese açık | API sağlık durumunu döndürür. |

## HTTP durum kodları

- `200`: okuma veya güncelleme başarılı.
- `201`: kayıt oluşturuldu.
- `204`: işlem başarılı; yanıt gövdesi yok.
- `400`: geçersiz gövde, zaman aralığı veya alan kuralı.
- `401`: token eksik/geçersiz ya da kullanıcı adı/parola hatalı.
- `403`: rol veya kaynak sahipliği izni yok.
- `404`: kaynak bulunamadı.
- `409`: kullanıcı adı çakışması, kapasite doluluğu veya mevcut durumla çakışan işlem.

## Geliştirme kontrolleri

Backend testleri:

Test uygulama bağlamını başlatır ve varsayılan PostgreSQL ayarlarını kullanır; testten önce PostgreSQL'i yukarıdaki Compose adımıyla çalıştırın.

```powershell
cd backend
.\mvnw.cmd test
```

Frontend TypeScript kontrolü ve üretim derlemesi:

```powershell
cd frontend
npm run build
```

API uçlarının CLI ile denenmiş akışları için [API-CLI-TEST-REPORT.md](API-CLI-TEST-REPORT.md) dosyasına bakın.

## Dizin yapısı

```text
backend/                       Spring Boot API
  src/main/java/com/slotly/api/ controller, service, repository, entity, dto, security
  src/main/resources/           uygulama yapılandırması
  src/test/                     backend testleri
frontend/                      Vue 3 + TypeScript + Vite
  src/App.vue                   Vue Router görünüm kapsayıcısı
  src/pages/                    ana ekran, giriş ve kayıt sayfaları
compose.yaml                   yerel PostgreSQL 17 servisi
dev.mjs                        PostgreSQL, backend ve frontend'i tek komutla başlatır
API-CLI-TEST-REPORT.md         API uçları ve hata akışları raporu
```

## Durdurma ve yerel veriler

API ve Vite süreçlerini çalıştıkları terminallerde `Ctrl+C` ile durdurun. PostgreSQL'i proje kökünden durdurmak için:

```powershell
docker compose down
```

Bu komut veritabanı volume'unu korur. Yerel veritabanını ve içindeki verileri de silmek için `docker compose down -v` kullanılır.

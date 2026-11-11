# Slotly

Randevu ve otomatik bekleme listesi yönetimi. İlk sürüm: Spring Boot API, Vue 3/TypeScript arayüzü ve PostgreSQL.

## Gereksinimler

- Java 25+
- Node.js 24+
- Docker Desktop (PostgreSQL için)

## Yerel geliştirme

1. PostgreSQL'i başlatın: `docker compose up -d postgres`
2. API'yi başlatın: `cd backend` ardından `./mvnw spring-boot:run` (Windows: `./mvnw.cmd spring-boot:run`)
3. Diğer terminalde arayüzü başlatın: `cd frontend` ardından `npm run dev`
4. Vite adresi varsayılan olarak `http://localhost:5173`, API `http://localhost:8080`.

Veritabanı ayarları `DATABASE_URL`, `DATABASE_USERNAME` ve `DATABASE_PASSWORD` ortam değişkenleriyle değiştirilebilir. Yerel varsayılanlar yalnız geliştirme içindir.

## Yapı

- `backend/`: Spring Boot 4.1.2, Java 25, Maven Wrapper, JPA, PostgreSQL, Validation, Actuator
- `frontend/`: Vue 3, TypeScript, Vite
- `compose.yaml`: PostgreSQL 17

## İlk sürüm alan modeli ve API taslağı

İlk sürüm, tek Spring Boot uygulaması ve PostgreSQL üzerinde çalışır. Fotoğraf yükleme ve bekleme listesi bu sürümün dışındadır.

### Varlıklar

- `User`: `id`, benzersiz `username`, `passwordHash`, `firstName`, `lastName`, `role` (`CUSTOMER` veya `OWNER`). E-posta gerekmez. Parola BCrypt ile hashlenir.
- `Place`: `id`, `owner_id`, `name`, `description`, `address`, `capacity` (pozitif tam sayı), aktiflik bilgisi. Bir işletme sahibinin birden çok mekânı olabilir.
- `BusinessHour`: `place_id`, haftanın günü, açılış/kapanış saati ve kapalı bilgisi. Mekânın her günü için sıfır veya daha fazla çalışma aralığı tanımlanabilir.
- `Offering`: `id`, `place_id`, `name`, `description`, `price`, `durationMinutes` (isteğe bağlı), `kind` (`SERVICE` veya `PRODUCT`). Hizmet ve ürün aynı esnek modelde tutulur. Randevuya yalnızca süresi tanımlı hizmet bağlanır; ürünler listelenir ve fiyatlandırılır, ilk sürümde randevu takvimine alınmaz. Böylece işletme türü bilinmeden ürünler dışlanmaz ve ürünlere yapay randevu süresi atanmaz.
- `Appointment`: `id`, `customer_id`, `place_id`, `offering_id`, `startsAt`, `endsAt`, `status`. Oluşturma sırasında bitiş, hizmet süresinden hesaplanır; süresiz ürün için varsayılan slot uzunluğu ürün kuralı olarak yapılandırılmalıdır.

### API

- `POST /api/auth/register` — kullanıcı adı, parola, ad, soyad ve rol ile kayıt; kullanıcı adı çakışmasında `409`.
- `POST /api/auth/login` — kullanıcı adı/parola ile giriş, JWT döndürür.
- `GET /api/places`, `GET /api/places/{id}`, `GET /api/places/{id}/offerings` — herkese açık keşif.
- `POST|PUT|DELETE /api/owner/places[/{id}]` — yalnız OWNER; kaynak sahipliği doğrulanır.
- `POST|PUT|DELETE /api/owner/places/{placeId}/offerings[/{id}]` ve çalışma saatlerini yönetme uçları — yalnız mekân sahibi.
- `GET /api/places/{id}/availability?offeringId=&from=&to=` — slot uygunluğu ve dolu slotlarda müşteri adı/soyadı; diğer kişisel alanlar dönmez.
- `POST /api/appointments` — yalnız CUSTOMER; müşteri kimliği JWT öznesinden alınır, istekteki müşteri kimliği kabul edilmez.
- `GET /api/appointments/me`, `DELETE /api/appointments/{id}` — müşterinin kendi randevuları.
- `GET /api/owner/appointments?placeId=&from=&to=` — OWNER için yalnız kendi mekânlarının randevuları ve müşteri bilgileri.
- `GET /api/places/{id}/business-hours`, `POST|DELETE /api/owner/places/{placeId}/business-hours[/{id}]` — çalışma aralıklarını görüntüleme ve yönetme.
- `GET /api/waitlist/me`, `POST /api/waitlist`, `POST /api/waitlist/{id}/accept|decline`, `DELETE /api/waitlist/{id}` — bekleme listesine katılma ve teklifleri yönetme.

### Temel kurallar

Randevu, seçilen hizmetin mekâna ait ve etkin olduğunu; başlangıç/bitişin yerel çalışma aralığına sığdığını; başlangıç zamanının gelecekte olduğunu doğrular. Kapasite, randevuların zaman aralığı çakışmasına göre hesaplanır (`startsAt < requestedEnd` ve `endsAt > requestedStart`); kapasiteyi yalnız aynı başlangıç saatinde saymak uzun hizmetlerde aşım yaratır. İptal edilmiş randevular kapasite tüketmez.

Eşzamanlı rezervasyonlarda mekân satırı `PESSIMISTIC_WRITE` kilidiyle kilitlenir; kapasite kontrolü ve kayıt aynı transaction içinde yapılır. Böylece aynı mekân için yarışan işlemler sırayla kapasiteyi kontrol eder. Benzersiz kullanıcı adı ayrıca veritabanı unique constraint'i ile korunur. JWT doğrulaması Spring Security filtresinde; rol ve kaynak sahipliği kontrolleri servis/API katmanında uygulanır.

Randevu saatleri veritabanında `timestamptz`/Java `Instant` olarak, çalışma saatleri mekânın saat dilimiyle yorumlanmalıdır. Mekân için saat dilimi alanı eklenmeden çok bölgeli işletim güvenilir olmaz; ilk sürümde tek saat dilimi varsayımı açıkça yapılandırılmalıdır.

İlk sürümde varsayılan çalışma saatleri saat dilimi `Europe/Istanbul` değeridir; `BUSINESS_TIME_ZONE` ile değiştirilebilir. Uygun başlangıç saatleri çalışma aralığının açılışından itibaren hizmet süresi kadar adımlarla üretilir. Kapasite dolu bir saat için bekleme listesine katılınabilir. Randevu iptalinde sıradaki müşteri FIFO sırayla uygulama içi teklif alır; teklif 30 dakika içinde kabul edilmezse sıradaki kişiye geçer.

Bu belge ilk sürüm uygulama sözleşmesini tanımlar. Randevu akışında `Offering.kind=SERVICE` ve `durationMinutes > 0` koşulu aranır; `PRODUCT` kayıtları keşif ve fiyat listesinde görünür, randevu oluşturma isteğinde reddedilir.

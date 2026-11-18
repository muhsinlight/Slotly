# Kurulum ve yapılandırma

[← Slotly](../README.md)

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
pnpm install --frozen-lockfile
pnpm run dev
```

Vite `http://localhost:5173` adresinde açılır. Tarayıcıda bu adresi ziyaret edin. Arayüz API'ye `http://localhost:8080/api` üzerinden bağlanır; API önce çalışıyor olmalıdır.

Farklı API ortamları için `frontend/.env.example` örneğindeki `VITE_API_BASE_URL` değerini derlemeden önce ayarlayın. Bu değer API origin'idir; `/api` uygulama tarafından eklenir. Vite değişkenleri derlemeye gömülür, sır içermemelidir.

PostgreSQL, API ve arayüzü tek komutla başlatmak için proje kökünde çalıştırın:

```powershell
node .\scripts\dev.mjs
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

### Ayrı demo ortamı

Demo kendi `slotly_demo` veritabanı, 5435 portu ve `slotly-demo` Compose projesini kullanır. Tek örnek işletme, iki hizmet, haftalık çalışma saatleri ve iki hesap oluşturur. Yeniden başlatmak mevcut demo işletmesini çoğaltmaz.

```powershell
docker compose -f docker/compose.demo.yaml up -d --wait
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

İkinci terminalde arayüz:

```powershell
cd frontend
$env:VITE_API_BASE_URL = 'http://localhost:8082'
pnpm run dev --port 5174 --strictPort
```

`http://localhost:5174` üzerinde `demo-owner` veya `demo-customer` ile giriş yapılabilir; yalnız bu yerel demo hesaplarının parolası `SlotlyDemo2026!` olur. Demo profili `prod` ile birlikte çalışmayı ve farklı adlı veritabanına örnek veri yazmayı reddeder. Demo veritabanını durdurmak için `docker compose -f docker/compose.demo.yaml down` kullanılır.

Backend aşağıdaki değerleri süreç ortam değişkenlerinden okur. Varsayılanlar yerel geliştirme içindir.

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `SERVER_PORT` | `8080` | API portu |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/slotly` | PostgreSQL JDBC adresi |
| `DATABASE_USERNAME` | `slotly` | Veritabanı kullanıcısı |
| `DATABASE_PASSWORD` | `slotly` | Yerel Compose parolası. Değiştirirseniz `compose.yaml` içindeki PostgreSQL parolasıyla eşleştirin. |
| `JWT_SECRET` | Yerel geliştirme anahtarı | JWT imzalama anahtarı. Yerel varsayılanı canlı ortamda kullanmayın; dış ortamda gizli, güçlü ve en az 32 baytlık bir anahtar sağlayın. |
| `BUSINESS_TIME_ZONE` | `Europe/Istanbul` | Çalışma saatlerinin yorumlandığı saat dilimi |
| `CORS_ALLOWED_ORIGINS` | Yerel Vite adresleri | Virgülle ayrılmış izinli arayüz origin'leri; canlı profilde açıkça ayarlanmalıdır. |
| `LOGIN_RATE_LIMIT_ATTEMPTS` | `10` | IP başına pencere içinde izin verilen giriş isteği sayısı. |
| `LOGIN_RATE_LIMIT_WINDOW_SECONDS` | `60` | Giriş sınırının saniye cinsinden penceresi. |
| `SPRING_PROFILES_ACTIVE` | — | Canlı ayarlar için `prod`; eksik DB/CORS/JWT değerlerinde başlangıcı başarısız yapar. |

PowerShell'de yalnızca o terminal oturumu için ortam değişkeni ayarlama örneği:

```powershell
$env:JWT_SECRET = 'yerel-gelistirme-icin-guclu-ve-ozel-bir-anahtar'
cd backend
.\mvnw.cmd spring-boot:run
```

Şema Flyway tarafından yönetilir; Hibernate yalnızca şemayı doğrular. `V1__create_slotly_schema.sql` temiz veritabanı kurar. İlk geçişte Flyway mevcut ve boş olmayan Hibernate veritabanını sürüm 1'de baseline alır; mevcut kayıtları yeniden oluşturmadan korur. Sonraki şema değişikliklerini yeni sürümlü migration'larla ekleyin.

Canlı profil için örnek değişkenler [backend/.env.example](../backend/.env.example) içindedir. Dosya Spring tarafından otomatik yüklenmez; dağıtım ortamında değerleri süreç ortamına veya secret yöneticisine tanımlayın. `prod` profilini yalnızca HTTPS sonlandıran, dışarıdan doğrudan erişilemeyen ters proxy arkasında çalıştırın. JWT anahtarı en az 32 bayt rastgele değer olmalı; CORS listesi yalnızca gerçek arayüz adreslerini içermelidir. Giriş sınırı uygulama belleğinde ve sunucu başına tutulur; birden fazla API kopyasında ortak sınırlayıcı/ters proxy katmanı olmadan küresel sınır oluşturmaz.

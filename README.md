# Slotly

Randevu ve otomatik bekleme listesi yönetimi. Müşteriler uygun saatleri bulup randevu alır; işletmeler hizmetlerini, çalışma saatlerini ve randevularını tek panelden yönetir.

Bir randevu iptal edildiğinde boşalan saat, bekleme listesindeki sıradaki müşteriye uygulama içinde teklif edilir. Teklif 30 dakika geçerlidir; reddedilirse veya süresi dolarsa sıra ilerler.

## Özellikler

- Mekân ve hizmet keşfi, uygun saat seçimi ve randevu iptali.
- İlk gelenin önce teklif aldığı bekleme listesi ve anlık teklif bildirimleri.
- İşletmeler için hizmet/ürün, çalışma saati ve günlük/haftalık randevu yönetimi.
- Yöneticiler için tüm işletmelerin randevu ve bekleme kayıtlarını gösteren salt okunur panel.
- Müşteri ve işletme sahibi rolleriyle JWT kimlik doğrulama.

## Teknolojiler

**Backend:** Java 25, Spring Boot, Spring Security, JPA, Flyway ve PostgreSQL 17.

**Frontend:** Vue 3, TypeScript, Vue Router ve Vite.

## Hızlı başlangıç

JDK 25, Node.js 24+, pnpm 12, Docker Compose ve Git gerekir. Docker çalışırken depo kökünde:

```sh
node scripts/dev.mjs
```

Komut yerel PostgreSQL’i başlatır, gerektiğinde frontend bağımlılıklarını kurar ve API ile web arayüzünü çalıştırır.

RabbitMQ ile bildirim kuyruğunu denemek için `docker compose --profile messaging up -d --wait rabbitmq`
komutunu çalıştırın ve API'yi `RABBITMQ_ENABLED=true` ortam değişkeniyle başlatın.
RabbitMQ yönetim ekranı `http://localhost:15672` adresindedir (yerel kullanıcı/şifre: `slotly`).
E-posta sağlayıcısı bağlı değilken e-posta işleri veritabanında bekler; yayınlama ve gönderim başlamaz.

- Web: http://localhost:5173
- API: http://localhost:8080
- Sağlık kontrolü: http://localhost:8080/actuator/health

Arayüzden işletme sahibi hesabı oluşturun; mekân, çalışma saatleri ve hizmet ekleyin. Randevu almak için müşteri hesabıyla giriş yapın. Hazır hesaplarla denemek için [ayrı demo ortamını](docs/SETUP.md#ayrı-demo-ortamı) kullanabilirsiniz.

İlk yönetici hesabını oluşturmak için API'yi başlatmadan önce `SLOTLY_ADMIN_USERNAME` ve en az 12 karakterli `SLOTLY_ADMIN_PASSWORD` ortam değişkenlerini ayarlayın. Uygulama hesabı yalnızca yoksa oluşturur; sonraki açılışlarda parolayı değiştirmez. Bu hesapla giriş yaptıktan sonra `/admin` paneli tüm işletmelerin randevu ve bekleme kayıtlarını gösterir. Açık kayıt formu yönetici rolünü kabul etmez.

`Ctrl+C` uygulamaları durdurur. PostgreSQL’i durdurmak için `docker compose down` çalıştırın; kayıtlı veriler korunur.

## Dokümantasyon

- [Kurulum, demo ve ortam değişkenleri](docs/SETUP.md)
- [API referansı ve alan kuralları](docs/API.md)
- [Testler, CI ve yedekleme](docs/DEVELOPMENT.md)
- [Yol haritası ve açık kararlar](docs/ROADMAP.md)

## Proje yapısı

```text
backend/       Tek Spring Boot uygulaması
  src/main/java/com/slotly/api/
    common/    Ortak ayarlar, güvenlik, yanıt ve hata yönetimi
    module/    user, auth, place, booking ve waitlist alanları
  src/main/resources/  Ayarlar ve migration dosyaları
  src/test/    Backend testleri
frontend/      Vue uygulaması ve tarayıcı testleri
docs/          Kurulum, API ve geliştirme rehberleri
docker/        Ayrı demo ve test veritabanları
scripts/       Geliştirme, test ve Git kancası araçları
.github/       CI iş akışları
```

`compose.yaml` yerel geliştirme veritabanını tanımlar. `docker/compose.demo.yaml`, `docker/compose.integration-test.yaml` ve `docker/compose.browser-test.yaml` demo ve test ortamlarını ayrı veritabanlarında çalıştırır. Bu dosyalar tekrar kurulabilir geliştirme ortamının parçasıdır.

`common` ve `module` aynı uygulamanın Java paketleridir; ayrı Maven projeleri değildir. Her alan kendi controller, DTO, entity, repository ve service katmanlarını içerir. Paket düzeni [aviation-platform örneğini](https://github.com/Mervearslan1/aviation-platform/tree/main/src/main/java/com/aviation/platform) izler; Slotly’nin mevcut sürümleri ve alan davranışları korunur.

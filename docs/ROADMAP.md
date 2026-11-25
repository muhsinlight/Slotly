# Slotly — durum ve kapsam

Slotly'nin mevcut hedefi, GitHub'da incelenebilen ve Docker ile yerelde çalıştırılabilen bir portföy demosudur. Barındırılan üretim ortamı veya canlı pilot planı yoktur.

## Tamamlanan ürün akışları

- Müşteri mekân ve hizmetleri inceler, uygun saat seçer, randevu alır, iptal eder veya saatini değiştirir.
- Kapasite dolduğunda müşteri FIFO bekleme listesine katılır. Boşalan saat sıradaki kişiye süreli teklif edilir; teklif kabul edilir, reddedilir veya süresi dolunca sıradaki kişiye geçer.
- İşletme sahibi mekânını, hizmetlerini, haftalık saatlerini ve özel günlerini yönetir; randevuları tamamlandı veya gelmedi olarak işaretler.
- Hesap akışlarında e-posta doğrulama isteği, parola kurtarma, bildirim tercihleri ve hatırlatma altyapısı bulunur.
- Salt okunur yönetici görünümü tüm işletmelerin randevu ve bekleme listesi kayıtlarını gösterir.

## Teknik doğrulama

- Backend Spring Boot, JPA ve PostgreSQL kullanır; Flyway migration'ları temiz kurulum ve mevcut şema senaryolarını kapsar.
- Backend entegrasyon testleri kapasiteyi, eşzamanlı rezervasyonları, FIFO davranışını, yetki sınırlarını ve zaman kurallarını doğrular.
- GitHub Actions backend testlerini PostgreSQL 17 üzerinde, frontend derlemesini ve tarayıcı akışlarını çalıştırır. Güncel sonuç CI iş akışındadır.
- Yerel geliştirme ve doğrulama adımları [DEVELOPMENT.md](DEVELOPMENT.md), demo kurulumu [SETUP.md](SETUP.md) içindedir.

## Bilinen sınırlar

- E-posta işlemleri outbox'a kaydedilir. Bir e-posta sağlayıcısı/adaptörü bağlanmadığı için gerçek e-posta gönderilmez.
- Uygulama tek backend örneği varsayar. Giriş hız sınırları ve SSE bağlantıları süreç belleğindedir; yatay ölçekleme desteklenmiş değildir.
- JWT tarayıcıda localStorage içinde saklanır. Oturum yenileme yaklaşımı canlı pilot öncesi ayrıca kararlaştırılmalıdır.
- Üretim ortamı, HTTPS alan adı, üretim secret'ları ve uzaktan yedek saklama ayarları kurulmamıştır.

Bu sınırlar mevcut GitHub/demo hedefi için kabul edilmiştir; canlı pilot hedefi seçilirse birlikte ele alınır. Ayrıntılı teknik notlar [geliştirme rehberindedir](DEVELOPMENT.md).

## Ertelenen fikirler

Çalışan bazlı takvim, kullanım raporları, gelişmiş konum/kategori araması, takvim eşitleme, ödeme ve kapora gibi işler için doğrulanmış bir ürün ihtiyacı yoktur. Bu kapsam, pilot veya kullanıcı geri bildirimi doğana kadar açık bir teslimat planı sayılmaz.

## Sonraki adım

Şu an planlanmış bir ürün geliştirme kilometre taşı yok. Yeni kapsam ancak net bir kullanıcı ihtiyacı veya proje sunumuna anlamlı katkı olduğunda eklenir; alan kuralları değiştiğinde ilgili testler ve belgeler birlikte güncellenir.

# Slotly geliştirme planı

Bu dosya mevcut davranışı ve açık ürün kararlarını özetler. Test sonuçlarının kaynağı ilgili commit'in CI çalışmasıdır; farklı oturumların sonuçları güncel durum olarak birleştirilmez. Değişiklik yapan oturum yalnız kendi kapsamını günceller; başka dalda kalan veya doğrulanmamış işi tamamlandı olarak işaretlemez.

## Mevcut uygulama durumu

| Aşama | Uygulananlar | Açık kalanlar |
|---|---|---|
| 1. Müşteri akışı | Arama, mekân detayı, hizmet/tarih/saat, özet/onay, giriş ve kayıttan seçimle dönüş, ayrı randevu sayfası, iptal onayı, 401/oturum temizliği | Temel tamamlanma ölçütleri mobil tarayıcı testinde doğrulandı. |
| 2. Güvenilirlik | İzole PostgreSQL, enjekte edilen saat, eşzamanlı rezervasyon/kabul, teklif kapasitesi, kapasite azaltımında mevcut randevu ve aktif teklif koruması, rol/sahiplik testleri ve CI | Yeni alan kuralları ilgili testlerle eklenir. |
| 3. İşletme paneli | Mekân/hizmet/saat yönetimi, günlük/haftalık liste ve özel günler; mevcut randevu ve teklifleri bozan takvim değişiklikleri engellenir | Yeniden planlama bekliyor. Tamamlandı/gelmedi akışı eklendi; doğrulama durumu ilgili çalıştırmada raporlanır. |
| 4. Bekleme listesi ve hesap | FIFO, süreli teklif, SSE ve yedek yenileme; e-posta doğrulama, parola kurtarma, bildirim tercihleri, kalıcı gönderim kuyruğu ve 2/24 saat hatırlatma altyapısı | Sağlayıcı adaptörü ve gerçek doğrulama/kurtarma/teklif/hatırlatma e-postası teslimatı bekliyor. |
| 5. Pilot hazırlık | Flyway V1, temiz kurulum ve mevcut kayıtları koruyan baseline testi, ortam API adresi, prod secret/CORS ayarları, giriş sınırı, sağlık/hata kayıtları, otomatik dump/restore kontrolü, ayrı demo | Gerçek canlı ortam, HTTPS proxy/domain, secret ve yedek saklama ayarları kurulmadı; sınırlı gerçek pilot başlamadı. |

Doğrulama komutları [DEVELOPMENT](DEVELOPMENT.md) içindedir. Yedek dönüşünde `backend/verify-restore.mjs` listesindeki tabloların kayıt sayıları ve içerik checksum'ları karşılaştırılır. Güncel test sayıları ve sonuçları ilgili çalıştırma raporundan okunur; burada tarihsel sayılar güncel sonuç gibi tutulmaz.

GitHub CI'da Linux Maven Wrapper çalıştırma izni hatası düzeltildi; `.gitattributes` LF düzenini korur. Görev dallarında uzak kontroller geçmeden `main` birleşimi yapılmaz. Aşağıdaki özgün kapsam ve tamamlanma ölçütleri, açık işler bitene kadar korunur.

## Hedef

Bir işletmenin hizmetlerini ve takvimini yönetebildiği; müşterinin randevu alabildiği, iptal edebildiği ve boşalan saat teklifini kaçırmadan değerlendirebildiği bir pilot ürün.

## Şu an elimizde olanlar

- Ayrı giriş/kayıt sayfaları, müşteri ve işletme sahibi rolleri.
- Mekân, hizmet/ürün ve çalışma saatleri için backend uçları.
- Hizmet süresine göre uygun saat bulma; kapasite kontrolüyle rezervasyon ve iptal.
- FIFO bekleme listesi, 30 dakikalık teklif, kabul/ret ve süre dolunca sıradaki kişiye geçiş.
- Ayrı müşteri, işletme ve bekleme listesi ekranları; SSE bildirimleri ve yedek yenileme.
- Yerel geliştirme için PostgreSQL ve tek komutla servis başlatma.

Backend testleri uygulama bağlamına ek olarak kapasite, eşzamanlı istekler, FIFO, süre sonu, rol/sahiplik, giriş sınırı ve migration davranışlarını kapsar. Teklif ile doğrudan rezervasyon yarışı testlerle kapsanır; yeni ürün kuralları ilgili testlerle birlikte eklenir.

## 1. Müşterinin randevu yolculuğu — temel akış uygulandı

**Durum:** Keşif, mekân detayı, randevular, işletme paneli ve bekleme listesi ayrı sayfalardır. Aşağıdaki kapsam, mevcut akışın korunacak tamamlanma ölçütlerini tanımlar.

**Kapsam**

- Mekân listesi ve ayrı mekân detay sayfası; mevcut ad/adres alanlarıyla arama.
- Hizmet → tarih/saat → özet → onay akışı. Özette işletme, hizmet, tarih, saat, süre ve fiyat.
- Giriş gerektiğinde seçilen hizmet ve zamanı koruyarak giriş sonrasında akışa dönme; onaydan önce uygunluğu yeniden kontrol etme.
- Randevularım sayfasında yaklaşan/geçmiş/iptal ayrımı ve iptal onayı.
- Yükleniyor, sonuç yok, saat dolu ve bağlantı hatası durumları; işlem sürerken tekrar gönderimi engelleme.
- Süresi dolmuş token ve API 401 yanıtında oturumu temizleme; güvenli uygulama içi dönüş adresiyle girişe yönlendirme.
- HomePage içinden ilgili sayfa/bileşen ve veri çağrılarını ayırma; bu iş için yeni bir state kütüphanesi ekleme gereği kaynaklara göre değerlendirilir.

**Tamamlanma ölçütü:** Müşteri telefonda mekân bulur, giriş yapar, seçimini kaybetmeden randevu alır; randevuyu listede görür ve iptal edebilir. Aynı saati başka biri alırsa anlaşılır hata görüp yeni saat seçebilir. Süresi dolan oturum kullanıcıyı kilitli bir ekranda bırakmaz.

**Etkilenen katmanlar:** Öncelikle frontend; mevcut API kullanılabilir. İlk sürümde mevcut liste üzerinde arama yeterlidir; sunucu tarafı filtreleme/sayfalama ayrıca ele alınır.

## 2. Rezervasyon ve sıra güvenilirliği

**Neden:** Ürünün temel vaadi kapasiteyi aşmamak ve sırayı doğru işletmek. Pilot kullanımdan önce bunları tekrarlanabilir testlerle güvenceye almalıyız.

- İzole PostgreSQL üzerinde entegrasyon testleri; geliştiricinin mevcut verisini kullanmama.
- Aynı saate eşzamanlı rezervasyon, kapasite > 1, farklı sürelerin çakışması ve çalışma saati sınırları.
- İptal → FIFO teklif → kabul/ret/zaman aşımı → sıradaki kişi zinciri.
- Teklif açıkken doğrudan rezervasyon ve teklif kabulünün yarışması; çift kabul/iptal gibi tekrar istekleri.
- Geçmiş tarih, yanlış rol ve başka kullanıcıya/işletmeye ait kaynağa erişim kontrolleri.
- Zaman testlerini beklemeden çalıştırabilmek için enjekte edilebilir saat kaynağı.
- CI içinde backend testleri ve frontend TypeScript/üretim derlemesi; müşteri ana akışı için tarayıcı testi.

**Tamamlanma ölçütü:** Tekrarlanabilir kontroller kapasite aşımını, mükerrer randevuyu ve sıra ihlalini yakalar; CI başarısızsa değişiklik birleştirilmez.

**Karar (Muhsin, 16 Kasım):** Teklif boyunca bir kişilik kapasite ayrılır. Kabul, ret, listeden ayrılma veya süre sonunda kapasite dönüşümü/serbest bırakılması testlerle doğrulanır.

## 3. İşletme sahibinin günlük çalışma ekranı

- Ayrı işletme paneli ve işletme seçimi.
- Günlük/haftalık randevu görünümü; saat, müşteri ve hizmet bilgileri.
- Mevcut API ile mekân/hizmet düzenleme ve devre dışı bırakma.
- Mevcut çalışma saatlerini listeleme, ekleme ve silme; çakışma hatalarını anlaşılır gösterme.
- İlk işletmeyi kurarken mekân → çalışma saatleri → hizmet adımları.
- Tatil/özel gün istisnaları için ayrı veri modeli ve migration; haftalık saatlerin üzerine uygulanması.

**Tamamlanma ölçütü:** İşletme sahibi API aracı açmadan bir mekân kurar, hizmetini ve saatlerini değiştirir, o günkü randevularını takip eder. Kapalı gün için yeni rezervasyon alınamaz.

**Karar (Muhsin, 16 Kasım):** Mevcut randevuyla çakışan değişiklik engellenir. Uygulama aktif teklifleri de korur; özel gün kaldırma ve haftalık saat silme aynı kontrolden geçer.

## 4. Bekleme listesini ürünün güçlü tarafı yapma

- Ayrı bekleme listesi ekranı; Türkçe ve tutarlı durum etiketleri.
- Sunucunun offerExpiresAt değerine dayalı kalan süre; süresi dolan teklifte kabul düğmesini kapatma ve sunucuyla yeniden doğrulama.
- Yeni teklif için uygulama içi bildirim ve okunmamış durumu.
- SSE ile açık sayfaya teklif iletimi; kopma/yeniden bağlanma ve yedek yenileme davranışı.
- Sonraki adım olarak izinli e-posta hatırlatmaları. Mevcut hesaplar kullanıcı adı temelli olduğundan önce e-posta alanı, doğrulama, tercih ve teslimat/tekrar deneme akışı gerekir.

**Tamamlanma ölçütü:** İptalden sonra uygun sıradaki kişi açık uygulamada teklifi görür; kabul/ret/zaman aşımı ekran ile sunucuda tutarlıdır. Tek bildirim birden fazla rezervasyon yaratmaz.

**Sınır:** SMS/WhatsApp, ücretli sağlayıcı ve dış bildirim gönderimi bu planla yetkilendirilmiş değildir. Kanal ve maliyet seçimi ayrı karardır.

## 5. Pilot yayına hazırlık

- Flyway gibi sürümlü migration akışı; mevcut veritabanını koruyan başlangıç geçişi ve temiz kurulum kontrolü.
- Frontend API adresini ortamdan okuma; geliştirme/canlı ortam ayrımı.
- Canlı ortam sırları, HTTPS, dar CORS listesi ve giriş denemelerine hız sınırlama.
- Oturum saklama/yenileme yaklaşımını belirleme; değişirse frontend, backend ve CSRF davranışını birlikte ele alma.
- Sağlık kontrolü, anlamlı hata kayıtları, yedekleme ve geri yükleme denemesi.
- Gerçek veriden ayrı örnek verilerle demo; tek işletme üzerinden sınırlı pilot.

**Tamamlanma ölçütü:** Temiz bir ortam kurulabilir, sürüm yükseltme mevcut veriyi korur, yedekten dönüş denenmiştir ve iki rolün uçtan uca akışları geçer.

## Daha sonra değerlendirilecekler

- Arama büyüdüğünde sunucu tarafı filtreleme ve sayfalama; kategori/konum modeli.
- İşletme için doluluk, iptal ve bekleme listesinden kazanılan randevu raporu.
- Takvime ekleme; önce ICS indirme, ihtiyaç doğrulanırsa Google/Outlook senkronizasyonu.
- Çalışan bazlı takvim, birden fazla şube ve randevu yeniden planlama.
- Ödeme/kapora ve no-show politikaları: pilot geri bildirimi ve işletme kuralları netleştikten sonra.

## Uygulama sırası

1 → 2 → 3 → 4 → 5. Her aşama küçük, bağımsız görevler halinde doğrulanıp commit edilir. Takvim ve bildirim özelliklerinden önce temel müşteri akışı tamamlanır; pilot yayından önce güvenilirlik ve migration işleri bitirilir. Süre tahmini, aşama görevleri ve ürün kararları netleşince yapılır.

Kapasite ayırma ve tarih istisnaları uygulanıp main'e birleşti. E-posta doğrulama ve parola kurtarma da eklendi. Teklif bildirimi ve randevu hatırlatma altyapısı gerçek gönderim kapalı olarak hazırlandı. Yeniden planlamada boşalan saatin FIFO davranışı kullanıcı kararını bekliyor. Tamamlandı/gelmedi işlemleri, Muhsin’in onayıyla yalnız işletme sahibine randevu bitişinden itibaren açılır.

## Bağımsız görevler ve inceleme düzeni

Her satır ayrı görev dalında uygulanır; görev büyürse bağımsız alt görevlere bölünür. İlgili kontroller geçince Conventional Commit ve push yapılır. GitHub CI başarılı olduktan sonra main'e birleştirilir ve biten dal temizlenir. Her commit sonrası dosya/metot, önce–sonra kod farkı, gerekçe, doğrulama sonucu ve commit bağlantısı kullanıcıya gösterilir.

| Sıra | Görev | Tamamlanma ölçütü / bağımlılık |
|---|---|---|
| 1 | Bekleme teklifinde kapasite ayırma | Uygulandı; doğrudan rezervasyon/kabul yarışı, ret, süre sonu ve kapasite > 1 testleri. |
| 2 | Tatil ve özel çalışma saatleri | Uygulandı; migration, API, işletme arayüzü ve rezervasyon engeli birlikte doğrulanır. |
| 3 | E-posta doğrulama | Uygulandı: hesap ekranı, adres ekleme, 30 dakikalık tek kullanımlık doğrulama, yeniden gönderim sınırı ve kalıcı gönderim kuyruğu. Sağlayıcı seçimi ve gerçek gönderim bekliyor. |
| 4 | Şifre sıfırlama | Uygulandı: doğrulanmış adres, 15 dakikalık tek kullanımlık bağlantı, genel yanıt, istek sınırı, eski oturumların iptali ve frontend kurtarma ekranları. Gerçek gönderim bekliyor. |
| 5 | Teklif bildirimi | Altyapı uygulandı: doğrulanmış adres, tercih ekranı, tekilleştirilmiş outbox, geçerlilik kontrolü, yeniden deneme ve bildirim geçmişi. Sağlayıcı adaptörü ve gerçek dış gönderim bekliyor. |
| 6 | Randevu hatırlatması | Altyapı uygulandı: frontend tercihleriyle 2/24 saat, zamanlayıcı, iptal/saat değişikliği ve mükerrer kayıt kontrolü. Gerçek gönderim sağlayıcı kararını bekliyor. |
| 7 | Randevu yeniden planlama | Eski randevu ile yeni kapasite atomik değişir; hata durumunda mevcut randevu korunur. İptal edilen saatin sıra davranışı kararlaştırılır. |
| 8 | Tamamlandı / gelmedi durumları | Yetkili işletme işlemi, izin verilen durum geçişleri ve müşteri/işletme görünümü doğrulanır. |
| 9 | Pilot hazırlığı | Hedef ortam, domain ve maliyet seçimi sonrası HTTPS, secret, izleme ve gerçek yedekten dönüş doğrulanır. |

Çalışan takvimi, raporlar, gelişmiş keşif, takvime ekleme ve ödeme pilot geri bildirimine göre sıralanır.

**Sonraki kararlar:** E-posta sağlayıcısı, gerçek dış gönderim yetkisi ve pilot barındırma hedefi henüz seçilmedi. Bu işlemler ayrı karardır.

## Dayanaklar

- [README](../README.md): mevcut API ve alan kuralları.
- [HomePage.vue](../frontend/src/pages/HomePage.vue): mekân keşfi; [waitlist.ts](../frontend/src/composables/waitlist.ts): SSE ve yedek yenileme.
- [router.ts](../frontend/src/router.ts), [auth.ts](../frontend/src/composables/auth.ts), [api.ts](../frontend/src/lib/api.ts): mevcut rotalar ve oturum/istek yönetimi.
- [BookingServiceImpl.java](../backend/src/main/java/com/slotly/api/module/booking/service/impl/BookingServiceImpl.java): kapasite, FIFO ve teklif davranışı.
- [application.properties](../backend/src/main/resources/application.properties): Flyway ve Hibernate schema validate ayarları.
- [BookingServiceIntegrationTests.java](../backend/src/test/java/com/slotly/api/BookingServiceIntegrationTests.java): rezervasyon ve sıra testleri; diğer entegrasyon testleri backend/src/test altında.
- [Cal.com](https://cal.com/) ve [SavvyCal](https://savvycal.com/): takvim ve rezervasyon akışını görünür kılan ürün sunumu örnekleri. Bu örnekler Slotly'nin alan kurallarını belirlemez.

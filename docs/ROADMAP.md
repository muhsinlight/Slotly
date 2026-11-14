# Slotly geliştirme planı

12 Kasım 2026. Mevcut kaynak kodu ve README üzerinden hazırlanmış plan ve uygulama durumu. Auth sayfalarının yeni sağ paneli kullanıcı tarafından görsel olarak beğenildi.

## Uygulama durumu — 12 Kasım 2026

| Aşama | Uygulananlar | Açık kalanlar |
|---|---|---|
| 1. Müşteri akışı | Arama, mekân detayı, hizmet/tarih/saat, özet/onay, giriş ve kayıttan seçimle dönüş, ayrı randevu sayfası, iptal onayı, 401/oturum temizliği | Temel tamamlanma ölçütleri mobil tarayıcı testinde doğrulandı. |
| 2. Güvenilirlik | İzole PostgreSQL, enjekte edilen saat, eşzamanlı rezervasyon/kabul, tekrar istekleri, kapasite ve süre çakışmaları, rol/sahiplik kontrolleri, CI ve gerçek API kullanan mobil testler | Teklif boyunca kapasite ayırma kuralı yanıt bekliyor; doğrudan rezervasyon/teklif yarışının nihai beklentisi bu kararla tamamlanacak. |
| 3. İşletme paneli | Ayrı panel, işletme seçimi, günlük/haftalık randevu listesi, mekân/hizmet düzenleme ve görünürlük, çalışma saati ekleme/silme, kurulum adımları | Mevcut randevuyla çakışan saat/tatil değişikliği kuralı yanıt bekliyor. Tarih istisnaları modeli, migration ve arayüzü henüz uygulanmadı. |
| 4. Bekleme listesi | Ayrı ekran, Türkçe durumlar, sunucu bitiş zamanına göre sayaç, süre dolunca yeniden doğrulama, tüm sayfalarda teklif/okunmamış durumu, SSE heartbeat/yeniden bağlanma ve yedek yenileme | E-posta doğrulama/tercih ve teslimat akışı sonraki adımdır; dış bildirim sağlayıcısı kurulmadı. |
| 5. Pilot hazırlık | Flyway V1, temiz kurulum ve mevcut kayıtları koruyan baseline testi, ortam API adresi, prod secret/CORS ayarları, giriş sınırı, sağlık/hata kayıtları, otomatik dump/restore kontrolü, ayrı demo | Gerçek canlı ortam, HTTPS proxy/domain, secret ve yedek saklama ayarları kurulmadı; sınırlı gerçek pilot başlamadı. |

Doğrulamalar: 16 backend testi, 3 mobil Chromium akışı ve frontend TypeScript/üretim derlemesi. Yedek dönüşünde altı tablonun kayıt sayısı ve içerik checksum'ları karşılaştırılır. Demo açılışı, müşteri girişi ve örnek işletme/hizmetler ayrıca denendi. Bunlar bütün alan uç durumlarının tamamlandığı anlamına gelmez.

GitHub CI'da Linux Maven Wrapper çalıştırma izni hatası düzeltildi; `.gitattributes` LF düzenini korur. Görev dallarında uzak kontroller geçmeden `main` birleşimi yapılmaz. Aşağıdaki özgün kapsam ve tamamlanma ölçütleri, açık işler bitene kadar korunur.

## Hedef

Bir işletmenin hizmetlerini ve takvimini yönetebildiği; müşterinin randevu alabildiği, iptal edebildiği ve boşalan saat teklifini kaçırmadan değerlendirebildiği bir pilot ürün.

## Şu an elimizde olanlar

- Ayrı giriş/kayıt sayfaları, müşteri ve işletme sahibi rolleri.
- Mekân, hizmet/ürün ve çalışma saatleri için backend uçları.
- Hizmet süresine göre uygun saat bulma; kapasite kontrolüyle rezervasyon ve iptal.
- FIFO bekleme listesi, 30 dakikalık teklif, kabul/ret ve süre dolunca sıradaki kişiye geçiş.
- Ana ekranda temel müşteri ve işletme işlemleri; bekleme teklifleri için 30 saniyelik yenileme.
- Yerel geliştirme için PostgreSQL ve tek komutla servis başlatma.

Backend işlevlerinin bulunması, bütün uç durumların otomatik testlerle doğrulandığı anlamına gelmez. Mevcut backend test dosyası yalnızca uygulama bağlamının açılmasını kontrol ediyor.

## 1. Müşterinin randevu yolculuğu — sıradaki geliştirme

**Neden:** Temel işlemler mevcut, fakat keşif, hesap, işletme yönetimi ve randevular tek ana ekran içinde toplanmış. Kullanıcıya sıradaki adımı açıkça göstermek ürünün en yakın kazanımı.

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

**Karar noktası:** 30 dakikalık teklif boyunca kapasite sıradaki kişi için tutulacak mı? Mevcut kabul işlemi kapasiteyi yeniden kontrol ediyor. Test ve API davranışını değiştirmeden bu ürün kuralı netleştirilmeli; öneri, FIFO beklentisini karşılayacak süreli kapasite ayırma davranışını değerlendirmek.

## 3. İşletme sahibinin günlük çalışma ekranı

- Ayrı işletme paneli ve işletme seçimi.
- Günlük/haftalık randevu görünümü; saat, müşteri ve hizmet bilgileri.
- Mevcut API ile mekân/hizmet düzenleme ve devre dışı bırakma.
- Mevcut çalışma saatlerini listeleme, ekleme ve silme; çakışma hatalarını anlaşılır gösterme.
- İlk işletmeyi kurarken mekân → çalışma saatleri → hizmet adımları.
- Tatil/özel gün istisnaları için ayrı veri modeli ve migration; haftalık saatlerin üzerine uygulanması.

**Tamamlanma ölçütü:** İşletme sahibi API aracı açmadan bir mekân kurar, hizmetini ve saatlerini değiştirir, o günkü randevularını takip eder. Kapalı gün için yeni rezervasyon alınamaz.

**Bağımlılık:** Tatil istisnaları backend ve veritabanı değiştirir. Mevcut randevular varken çalışma saatini değiştirme kuralı uygulamadan önce kararlaştırılır.

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

İlk somut görev: **Mekân detay sayfası ve hizmet/tarih seçimi**, ardından **randevu özeti/onayı ve girişten geri dönüş**.

## Dayanaklar

- [README](../README.md): mevcut API ve alan kuralları.
- [HomePage.vue](../frontend/src/pages/HomePage.vue): tek ekranda toplanmış akışlar ve periyodik yenileme.
- [router.ts](../frontend/src/router.ts), [auth.ts](../frontend/src/composables/auth.ts), [api.ts](../frontend/src/lib/api.ts): mevcut rotalar ve oturum/istek yönetimi.
- [BookingService.java](../backend/module/java/com/slotly/api/module/booking/service/BookingService.java): kapasite, FIFO ve teklif davranışı.
- [application.properties](../backend/app/resources/application.properties): mevcut Hibernate schema update yaklaşımı.
- [SlotlyApiApplicationTests.java](../backend/app/test/java/com/slotly/api/SlotlyApiApplicationTests.java): mevcut otomatik test kapsamı.
- [Cal.com](https://cal.com/) ve [SavvyCal](https://savvycal.com/): takvim ve rezervasyon akışını görünür kılan ürün sunumu örnekleri. Bu örnekler Slotly'nin alan kurallarını belirlemez.

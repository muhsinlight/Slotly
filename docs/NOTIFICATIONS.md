# E-posta bildirimleri

Hesap ayarlarındaki bildirim tercihleri varsayılan olarak kapalıdır. Açmak için doğrulanmış adres gerekir. `GET/PUT /api/account/notifications` tercihleri okur/yazar; `GET /api/account/notifications/deliveries` yalnız oturum sahibinin son 20 mesajının konu ve durumunu döndürür. Token, mesaj gövdesi ve başka kullanıcı kayıtları döndürülmez.

Aktif teklif olayı rezervasyon işlemiyle aynı transaction içinde outbox kaydı oluşturur. Teklif kimliğiyle tekilleştirilir. Gönderim öncesinde güncel adres, doğrulama, tercih, teklif durumu ve son kabul zamanı kontrol edilir. Geçersiz mesajlar iptal edilir.

Gönderim işçisi satırı `FOR UPDATE SKIP LOCKED` ile alır. Başarısızlıkta 1, 2, 4 ve 8 dakika aralıklarla yeniden dener. Beşinci başarısızlıkta durum `FAILED` olur. Başarı ve kalıcı başarısızlıkta gövde temizlenir.

Zamanlayıcı ve RabbitMQ tüketicisi aynı gönderim iznini ve sağlayıcı kontrolünü kullanır. Rabbit yayıncısı gönderilebilir en çok 100 işin kimliğini alır, bunları seçerken yeniden yayın zamanını veritabanına yazar. Yayıncı ya da tüketici durursa iki dakika sonra aynı kimlik yeniden yayınlanabilir. Birden çok teslimde işçi kaydın güncel durumunu ve deneme zamanını kontrol eder.

Hesap bağlantılarının temizliği, gönderim kapalıyken de dakikada bir çalışır. Süresi dolan veya tüketilen bağlantıların bekleyen mesajları `CANCELLED` durumuna alınır ve gövdeleri temizlenir. Önceki sürümden kalan kapalı mesajlar da temizliğe dahildir. Her geçiş en çok 500 mesaj işler. Mesaj kimliği, konu, durum ve deneme sayısı geçmişte kalır.

Gerçek dış gönderim kapalıdır (`slotly.email.enabled=false`). Sağlayıcı seçilmediği için üretim `EmailGateway` adaptörü yoktur. Yetkilendirilen adaptör mesaj UUID'sini sağlayıcının idempotency anahtarı olarak kullanmalıdır. Sağlayıcı bunu desteklemiyorsa gönderimden sonra DB commit'i öncesi çökme durumunda tekrar teslim riski vardır; bu sınır sağlayıcı seçiminde çözülmelidir. Testler yalnız sahte, yerel gateway kullanır.

Randevu hatırlatmaları `REMINDER` türünde aynı outbox ve gönderim işçisini kullanır. Tercih edilen 2/24 saat aralığında kuyruğa alınır; randevu iptali/saat değişikliği ve güncel adres/tercih gönderim anında yeniden doğrulanır. Ayrıntı: [REMINDERS](REMINDERS.md).

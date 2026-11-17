# E-posta bildirimleri

Hesap ayarlarındaki bildirim tercihleri varsayılan olarak kapalıdır. Açmak için doğrulanmış adres gerekir. `GET/PUT /api/account/notifications` tercihleri okur/yazar; `GET /api/account/notifications/deliveries` yalnız oturum sahibinin son 20 mesajının konu ve durumunu döndürür. Token, mesaj gövdesi ve başka kullanıcı kayıtları döndürülmez.

Aktif teklif olayı rezervasyon işlemiyle aynı transaction içinde outbox kaydı oluşturur. Teklif kimliğiyle tekilleştirilir. Gönderim öncesinde güncel adres, doğrulama, tercih, teklif durumu ve son kabul zamanı kontrol edilir. Geçersiz mesajlar iptal edilir.

Gönderim işçisi satır kilidi ve SKIP LOCKED kullanır; başarısızlıkta 1, 2, 4 ve 8 dakika aralıklarla yeniden dener, beşinci başarısızlıkta FAILED olur. Başarıda mesaj gövdesi temizlenir. Hesap bağlantılarında tüketilmiş/süresi dolmuş tokenlar gönderilmez.

Gerçek dış gönderim kapalıdır (`slotly.email.enabled=false`). Sağlayıcı seçilmediği için üretim `EmailGateway` adaptörü yoktur. Yetkilendirilen adaptör mesaj UUID'sini sağlayıcının idempotency anahtarı olarak kullanmalıdır. Sağlayıcı bunu desteklemiyorsa gönderimden sonra DB commit'i öncesi çökme durumunda tekrar teslim riski vardır; bu sınır sağlayıcı seçiminde çözülmelidir. Testler yalnız sahte, yerel gateway kullanır.

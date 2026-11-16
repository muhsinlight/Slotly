# Randevu hatırlatmaları

Hesap ayarlarında hatırlatma açılır; kullanıcı 2 veya 24 saat öncesini seçer. Varsayılan tercih kapalıdır. Doğrulanmış e-posta gerekir. Geçerli tercihler `reminderEmail` ve `reminderHours` alanlarıyla kaydedilir.

Zamanlayıcı dakikada bir, tercih edilen zaman aralığına girmiş ve henüz başlamamış onaylı randevuları kuyruğa alır. Kısa süre önce oluşturulmuş randevular için de aynı kontrol uygulanır. Randevu kimliği ve başlangıç saati aynıysa ikinci kayıt oluşturulmaz. Her taramada en fazla 200 kayıt işlenir; önceden kuyruklananlar sonraki kayıtları engellemez.

Gönderimden önce randevu durumu, saat, güncel doğrulanmış adres ve tercih kontrol edilir. İptal, saat değişikliği, başlangıç saatinin geçmesi veya tercihin kapanması eski mesajı iptal eder. Saat değişince yeni saat için ayrı hatırlatma oluşturulabilir. Gönderim ve tekrar deneme altyapısı için [NOTIFICATIONS](NOTIFICATIONS.md).

Gerçek dış gönderim henüz kapalıdır; sağlayıcı ve gönderim yetkisi ayrıca gerekir.

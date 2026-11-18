# Hesap ve e-posta akışı

Hesap ayarları `/account` ekranındadır. Mevcut kullanıcı adıyla giriş korunur.

- `GET /api/account/email` doğrulanmış adresi `email`, bekleyen talebi `pendingEmail` alanında gösterir. İlk adres henüz doğrulanıyorsa iki alanda da talep edilen adres görünür ve `verified` değeri `false` olur.
- `POST /api/account/email` ile gönderilen `{ "email": "..." }` yeni adrese 30 dakikalık doğrulama bağlantısı hazırlar. Önceki doğrulanmış adres bu süre boyunca bildirimleri ve kurtarma bağlantılarını almaya devam eder. Yeni istek için 60 saniye beklenir. Yeniden istek önceki doğrulama bağlantısını geçersiz kılar.
- `POST /api/auth/verify-email` tek kullanımlık `{ "token": "..." }` ile yeni adresi hesaba aktarır. Önceki adresin açık kurtarma bağlantıları o anda kapanır. Doğrulama süresi dolduğunda başka bir hesap aynı adres için istek açabilir.

Tokenlar veritabanında SHA-256 özetiyle tutulur. Bağlantıdaki token URL fragment alanındadır; doğrulama ekranı açılınca tarayıcı geçmişinden kaldırılır. Doğrulama kullanıcının düğmeye basmasını gerektirir.

Gerçek dış gönderim henüz etkin değildir. Mesajlar `email_outbox` tablosunda bekler; API yanıtı token veya mesaj gövdesi döndürmez. Sağlayıcı ve gerçek gönderim ayrı karar gerektirir. Yerel sentetik testte bağlantı yalnız test veritabanındaki outbox kaydından alınabilir.

Postman koleksiyonu: `backend/src/test/postman/account.postman_collection.json`. Yalnız izole test API'sine karşı çalıştırılır.

## Parola kurtarma

`/forgot-password` ekranı doğrulanmış adrese bağlantı ister. `POST /api/auth/forgot-password` e-posta alır; bilinmeyen, doğrulanmamış ve tekrar istenmiş adresler aynı genel yanıtı alır. Hesap başına bir dakikalık sınır ve IP tabanlı kötüye kullanım sınırı uygulanır.

`/reset-password#token=...` ekranı yeni parolayı iki kez ister. Kayıtta ve `POST /api/auth/reset-password` ile sıfırlamada parola en az 10 karakter olmalı ve UTF-8 kodlamasında 72 baytı aşmamalıdır. Örneğin `ş` iki bayt tutar. Kurtarma bağlantısı 15 dakika geçerlidir ve bir kez kullanılabilir. Parola değişince önceki tüm erişim tokenları `auth_version` kontrolüyle geçersiz olur.

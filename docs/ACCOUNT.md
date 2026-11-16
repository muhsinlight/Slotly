# Hesap ve e-posta akışı

Hesap ayarları `/account` ekranındadır. Mevcut kullanıcı adıyla giriş korunur.

- `GET /api/account/email`: giriş yapan kullanıcının adresi ve doğrulama durumu.
- `POST /api/account/email`: `{ "email": "..." }`; adresi kaydeder ve doğrulama isteğini kuyruğa alır. Yeniden istekler arasında 60 saniye gerekir.
- `POST /api/auth/verify-email`: `{ "token": "..." }`; 30 dakikalık tek kullanımlık bağlantıyı doğrular. Yeniden gönderim eski tokenı geçersiz kılar.

Tokenlar veritabanında SHA-256 özetiyle tutulur. Bağlantıdaki token URL fragment alanındadır; doğrulama ekranı açılınca tarayıcı geçmişinden kaldırılır. Doğrulama kullanıcının düğmeye basmasını gerektirir.

Gerçek dış gönderim henüz etkin değildir. Mesajlar `email_outbox` tablosunda bekler; API yanıtı token veya mesaj gövdesi döndürmez. Sağlayıcı ve gerçek gönderim ayrı karar gerektirir. Yerel sentetik testte bağlantı yalnız test veritabanındaki outbox kaydından alınabilir.

Postman koleksiyonu: `backend/src/test/postman/account.postman_collection.json`. Yalnız izole test API'sine karşı çalıştırılır.

## Parola kurtarma

`/forgot-password` ekranı doğrulanmış adrese bağlantı ister. `POST /api/auth/forgot-password` e-posta alır; bilinmeyen, doğrulanmamış ve tekrar istenmiş adresler aynı genel yanıtı alır. Hesap başına bir dakikalık sınır ve IP tabanlı kötüye kullanım sınırı uygulanır.

`/reset-password#token=...` ekranı yeni parolayı iki kez ister. `POST /api/auth/reset-password` token ve en az 10 karakterlik parola alır. Token 15 dakika geçerlidir ve yalnız bir kez kullanılır. Parola değişince önceki tüm erişim tokenları `auth_version` kontrolüyle geçersiz olur. Gerçek gönderim etkin değildir.

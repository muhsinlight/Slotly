# Hesap ve e-posta akışı

Hesap ayarları `/account` ekranındadır. Mevcut kullanıcı adıyla giriş korunur.

- `GET /api/account/email`: giriş yapan kullanıcının adresi ve doğrulama durumu.
- `POST /api/account/email`: `{ "email": "..." }`; adresi kaydeder ve doğrulama isteğini kuyruğa alır. Yeniden istekler arasında 60 saniye gerekir.
- `POST /api/auth/verify-email`: `{ "token": "..." }`; 30 dakikalık tek kullanımlık bağlantıyı doğrular. Yeniden gönderim eski tokenı geçersiz kılar.

Tokenlar veritabanında SHA-256 özetiyle tutulur. Bağlantıdaki token URL fragment alanındadır; doğrulama ekranı açılınca tarayıcı geçmişinden kaldırılır. Doğrulama kullanıcının düğmeye basmasını gerektirir.

Gerçek dış gönderim henüz etkin değildir. Mesajlar `email_outbox` tablosunda bekler; API yanıtı token veya mesaj gövdesi döndürmez. Sağlayıcı ve gerçek gönderim ayrı karar gerektirir. Yerel sentetik testte bağlantı yalnız test veritabanındaki outbox kaydından alınabilir.

Postman koleksiyonu: `backend/src/test/postman/account.postman_collection.json`. Yalnız izole test API'sine karşı çalıştırılır.

# API referansı

[← Slotly](../README.md)

## Özellikler ve temel kurallar

- Mekân, çalışma saatleri, hizmet ve ürün keşfi.
- Hizmet süresine göre uygun randevu saatlerini bulma.
- Randevu oluşturma, görüntüleme ve iptal etme.
- Mekân sahibi için mekân, hizmet/ürün, çalışma saati ve randevu yönetimi.
- Bekleme listesine katılma; teklif kabul etme, reddetme veya listeden ayrılma.
- Yalnız etkin ve pozitif süreli `SERVICE` kayıtları randevuya alınır. `PRODUCT` kayıtları keşif ve fiyat listesinde görünür, randevuya bağlanmaz.
- Uygun saatler çalışma aralığının açılışından başlayarak hizmet süresi kadar adımlarla üretilir. Arama aralığı en fazla 31 gündür.
- Kapasite, onaylı randevular ve süresi dolmamış bekleme tekliflerinin zaman aralığı çakışmalarına göre hesaplanır. Her teklif bir kişilik kapasiteyi en fazla 30 dakika ve en geç randevu başlangıcına kadar ayırır. Kabulde ayrılan yer randevuya dönüşür; ret, listeden ayrılma ve süre sonunda serbest kalır. Eşzamanlı rezervasyonlarda kontrol ve kayıt mekân kilidi altında yapılır.
- Teklif süresi dolunca sıradaki müşteriye geçiş, zamanlanmış kontrole ek olarak yeni rezervasyon isteğinde de mekân kilidi altında kontrol edilir; doğrudan rezervasyon sıradaki müşterinin yerini alamaz.
- Çalışma saatleri varsayılan olarak `Europe/Istanbul` saat diliminde yorumlanır. Mekân yanıtındaki `timeZone` alanı işletmenin IANA saat dilimini belirtir; istemci gün sınırlarını ve elle girilen saatleri bu bölgede hesaplar.
- Tarihe özel kapalı gün veya açılış–kapanış saati, o tarihin haftalık saatlerinin yerine geçer. Özel saat silinince haftalık takvim yeniden geçerli olur. Değişiklik onaylı bir randevuyu veya aktif bekleme teklifini çalışma saatleri dışında bırakırsa `409` ile reddedilir; haftalık saat silme işlemi de aynı kurala tabidir.

## Kimlik doğrulama ve yanıt biçimi

- Kayıt alanları: `username` (en fazla 40), `password` (en az 10 karakter ve en fazla 72 UTF-8 bayt), `firstName` ve `lastName` (en fazla 80), `role` (`OWNER` veya `CUSTOMER`). Aynı parola sınırı sıfırlama isteğinde de uygulanır.
- Korumalı uçlara `Authorization: Bearer <accessToken>` başlığı ekleyin. JWT ömrü 3600 saniyedir.
- Oturum sunucuda tutulmaz; cookie veya token yenileme ucu yoktur. Web arayüzü token'ı `localStorage` içindeki `slotly-token` anahtarında saklar. Çıkış token'ı tarayıcıdan siler.
- Pilot sürüm mevcut bir saatlik Bearer oturumunu kullanır. Süresi dolan token veya API 401 yanıtında oturum temizlenir ve uygulama içi dönüş adresiyle tekrar giriş istenir. Cookie ile kimlik doğrulama kullanılmadığından CSRF koruması kapalıdır; cookie/refresh değişikliği bu sözleşme ve CSRF korumasıyla birlikte ele alınmalıdır.
- JSON yanıtları `status`, `message`, `code`, `data` ve `timestamp` alanlarını içerir. `status`, HTTP durum kodudur; hata yanıtlarındaki `code` makinece okunabilir hata türünü belirtir. `204 No Content` yanıtlarında gövde yoktur.
- CORS için `http://localhost:5173` ve `http://127.0.0.1:5173` izinlidir.

### Hata yanıtları

İstek doğrulama hataları `400` ve alan-hata haritası içeren `data` ile döner. İş kuralı hataları uygun HTTP durumunu ve `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT` veya `GONE` gibi bir `code` değerini kullanır. Altyapı hatalarında veritabanı için `500` / `DATABASE_ERROR`, RabbitMQ için `503` / `RABBITMQ_ERROR` döner. Bu yanıtlar teknik ayrıntıları ve bağlantı bilgilerini içermez; ayrıntılar backend loglarına yazılır.

## API uçları

Tüm API uçları `/api` önekini kullanır. `{id}` değerleri UUID'dir. `from`, `to` ve `startsAt` alanlarını ISO-8601 biçiminde, saat dilimi/UTC offset belirterek gönderin.

### Kayıt, giriş ve hesap

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `POST /api/auth/register` | Herkese açık | Kullanıcı kaydeder; başarılı yanıt `201` döner (`ADMIN` rolü kabul edilmez). |
| `POST /api/auth/login` | Herkese açık | JWT erişim token'ı döndürür (IP başına hız sınırına tabidir). |
| `POST /api/auth/verify-email` | Herkese açık | Tek kullanımlık `{ "token": "..." }` ile e-posta adresini doğrular. |
| `POST /api/auth/forgot-password` | Herkese açık | Doğrulanmış e-posta adresine parola sıfırlama bağlantısı kuyruklar; adres varlığını ifşa etmez. |
| `POST /api/auth/reset-password` | Herkese açık | `{ "token": "...", "password": "..." }` ile parolayı yeniler ve eski oturumları geçersiz kılar. |
| `GET /api/account/email` | Oturum açık | Kullanıcının doğrulanmış ve doğrulama bekleyen e-posta durumunu döndürür. |
| `POST /api/account/email` | Oturum açık | `{ "email": "..." }` için 30 dakikalık doğrulama bağlantısı kuyruklar (60 sn yeniden istek sınırı). |
| `GET /api/account/notifications` | Oturum açık | E-posta teklif ve hatırlatma tercihlerini döndürür. |
| `PUT /api/account/notifications` | Oturum açık | Bildirim tercihlerini (`offerEmail`, `reminderEmail`, `reminderHours`: `2` veya `24`) kaydeder; doğrulanmış e-posta gerekir. |
| `GET /api/account/notifications/deliveries` | Oturum açık | Kullanıcının son 20 e-posta gönderim kaydının konu ve durum özetini döndürür. |

Kayıt gövdesi örneği:

```json
{
  "username": "ornek-musteri",
  "password": "Ornek-Sifre-2026",
  "firstName": "Ada",
  "lastName": "Yilmaz",
  "role": "CUSTOMER"
}
```

### Mekân keşfi

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `GET /api/places` | Herkese açık | Aktif mekânları listeler. |
| `GET /api/places/{id}` | Herkese açık | Aktif mekânı getirir. |
| `GET /api/places/{id}/offerings` | Herkese açık | Mekânın etkin hizmet ve ürünlerini listeler. |
| `GET /api/places/{id}/business-hours` | Herkese açık | Çalışma saatlerini listeler. |
| `GET /api/places/{id}/availability?offeringId={uuid}&from={instant}&to={instant}` | Herkese açık | Hizmet için uygun başlangıç zamanlarını döndürür; aralık en fazla 31 gündür. |

### İşletme yönetimi

Aşağıdaki uçlar yalnızca ilgili mekânın sahibi olan `OWNER` rolüyle kullanılabilir.

Özel günler için `{date}` biçimi `YYYY-MM-DD` şeklindedir. `PUT` gövdesi kapalı gün için `{"closed":true,"opensAt":null,"closesAt":null}`, özel saat için `{"closed":false,"opensAt":"12:00","closesAt":"17:00"}` olur. Bir tarih için tek özel aralık tanımlanabilir; aynı tarihe tekrar `PUT` kaydı günceller. Geçmiş tarihe `PUT` yapılmaz.

| Yöntem ve uç | Açıklama |
|---|---|
| `GET /api/owner/places/{placeId}/calendar-exceptions` | Özel günleri tarih sırasıyla listeler. |
| `PUT /api/owner/places/{placeId}/calendar-exceptions/{date}` | Kapalı gün veya özel saat oluşturur/günceller. |
| `DELETE /api/owner/places/{placeId}/calendar-exceptions/{date}` | Özel günü kaldırır; haftalık saate dönüş de çakışma kontrolünden geçer. |

| Yöntem ve uç | Açıklama |
|---|---|
| `GET /api/owner/places` | Sahibin mekânlarını listeler. |
| `POST /api/owner/places` | Mekân oluşturur: `name`, `address`, `capacity`, `active`; `description` isteğe bağlıdır. |
| `PUT /api/owner/places/{id}` | Mekânı günceller. |
| `DELETE /api/owner/places/{id}` | Mekânı devre dışı bırakır. |
| `GET /api/owner/places/{placeId}/offerings` | Mekânın etkin ve gizli tüm hizmet/ürünlerini listeler. |
| `POST /api/owner/places/{placeId}/offerings` | Hizmet veya ürün ekler. `SERVICE` için pozitif `durationMinutes` gerekir; `PRODUCT` için süre gönderilmez. |
| `PUT /api/owner/places/{placeId}/offerings/{id}` | Hizmet veya ürünü günceller. |
| `DELETE /api/owner/places/{placeId}/offerings/{id}` | Hizmet veya ürünü devre dışı bırakır. |
| `POST /api/owner/places/{placeId}/business-hours` | Haftanın günü için çalışma aralığı ekler. |
| `DELETE /api/owner/places/{placeId}/business-hours/{id}` | Çalışma aralığını siler. |
| `GET /api/owner/appointments?placeId={uuid}&from={instant}&to={instant}` | Sahibin mekân ve zaman aralığına göre randevularını listeler. |

### Yönetici görünümü

`GET /api/admin/overview?appointmentPage=0&waitlistPage=0&size=20&q=` yalnız `ADMIN` rolüne açıktır. Sayfalar sıfırdan başlar; `size` 1–100 arasındadır. Yanıttaki `appointments` ve `waitlist` seçili sayfanın kayıtlarıdır. `appointmentTotal` ve `waitlistTotal` arama sonucunun toplamlarını, `upcoming`, `waiting` ve `offers` genel sayaçları gösterir.

### Randevu ve bekleme listesi

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `POST /api/appointments` | `CUSTOMER` | `placeId`, `offeringId` ve gelecekteki `startsAt` ile randevu oluşturur. Bitiş hizmet süresinden hesaplanır. |
| `GET /api/appointments/me` | `CUSTOMER` | Müşterinin kendi randevularını listeler. |
| `PATCH /api/appointments/{id}/reschedule` | Randevu sahibi `CUSTOMER` | `{"startsAt":"2026-12-01T09:00:00Z"}` ile yaklaşan onaylı randevunun saatini aynı kimlikle değiştirir. Yeni saat doluysa eski randevu korunur; boşalan eski saat FIFO bekleme teklifine açılır. |
| `DELETE /api/appointments/{id}` | Randevu sahibi veya mekân sahibi | Başlangıç anından (`startsAt`) önceki onaylı randevuyu iptal eder; başlamış veya tamamlanmış randevu iptal edilemez (`409`). |
| `GET /api/waitlist/me` | `CUSTOMER` | Müşterinin bekleme kayıtlarını ve tekliflerini listeler. |
| `GET /api/waitlist/events` | `CUSTOMER` | `text/event-stream` üzerinden yeni teklifleri iletir. Akış kesilirse istemci yeniden bağlanır ve yedek olarak kayıtları yeniler. |
| `POST /api/waitlist` | `CUSTOMER` | Kapasitesi dolu bir hizmet ve saat için bekleme kaydı açar. |
| `POST /api/waitlist/{id}/accept` | `CUSTOMER` | Aktif teklifi kabul eder ve randevu oluşturur. |
| `POST /api/waitlist/{id}/decline` | `CUSTOMER` | Aktif teklifi reddeder. |
| `DELETE /api/waitlist/{id}` | `CUSTOMER` | Bekleme listesinden ayrılır. |

### Sağlık kontrolü

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `GET /actuator/health` | Herkese açık | API sağlık durumunu döndürür. |

## HTTP durum kodları

- `200`: okuma veya güncelleme başarılı.
- `201`: kayıt oluşturuldu.
- `204`: işlem başarılı; yanıt gövdesi yok.
- `400`: geçersiz gövde, zaman aralığı veya alan kuralı.
- `401`: token eksik/geçersiz ya da kullanıcı adı/parola hatalı.
- `403`: rol veya kaynak sahipliği izni yok.
- `404`: kaynak bulunamadı.
- `409`: kullanıcı adı çakışması, kapasite doluluğu veya mevcut durumla çakışan işlem.

## Randevu sonucu

`PATCH /api/owner/appointments/{id}/outcome`, `{"status":"COMPLETED"}` veya `{"status":"NO_SHOW"}` alır ve güncellenen randevuyu döndürür. Yalnız ilgili işletme sahibi, randevunun bitiş anından itibaren işlem yapabilir. Aynı sonuç tekrar gönderilebilir; iptal edilmiş veya farklı bir sonuçla kapanmış randevu değiştirilemez (`409`). Başka sahibin randevusu `403`, geçersiz hedef durum `400` döndürür. Tamamlanmış ve gelmedi işaretlenmiş randevular iptal edilemez. Yeni durumlar mevcut `VARCHAR(12)` alanına sığar; şema değişikliği gerekmez.

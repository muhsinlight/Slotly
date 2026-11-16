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
- Kapasite, onaylı randevular ve süresi dolmamış bekleme tekliflerinin zaman aralığı çakışmalarına göre hesaplanır. Her teklif bir kişilik kapasiteyi en fazla 30 dakika ayırır. Kabulde ayrılan yer randevuya dönüşür; ret, listeden ayrılma ve süre sonunda serbest kalır. Eşzamanlı rezervasyonlarda kontrol ve kayıt mekân kilidi altında yapılır.
- Teklif süresi dolunca sıradaki müşteriye geçiş, zamanlanmış kontrole ek olarak yeni rezervasyon isteğinde de mekân kilidi altında kontrol edilir; doğrudan rezervasyon sıradaki müşterinin yerini alamaz.
- Çalışma saatleri varsayılan olarak `Europe/Istanbul` saat diliminde yorumlanır.
- Tarihe özel kapalı gün veya açılış–kapanış saati, o tarihin haftalık saatlerinin yerine geçer. Özel saat silinince haftalık takvim yeniden geçerli olur. Değişiklik onaylı bir randevuyu veya aktif bekleme teklifini çalışma saatleri dışında bırakırsa `409` ile reddedilir; haftalık saat silme işlemi de aynı kurala tabidir.

## Kimlik doğrulama ve yanıt biçimi

- Kayıt alanları: `username` (en fazla 40), `password` (10–100), `firstName` ve `lastName` (en fazla 80), `role` (`OWNER` veya `CUSTOMER`).
- Korumalı uçlara `Authorization: Bearer <accessToken>` başlığı ekleyin. JWT ömrü 3600 saniyedir.
- Oturum sunucuda tutulmaz; cookie veya token yenileme ucu yoktur. Web arayüzü token'ı `localStorage` içindeki `slotly-token` anahtarında saklar. Çıkış token'ı tarayıcıdan siler.
- Pilot sürüm mevcut bir saatlik Bearer oturumunu kullanır. Süresi dolan token veya API 401 yanıtında oturum temizlenir ve uygulama içi dönüş adresiyle tekrar giriş istenir. Cookie ile kimlik doğrulama kullanılmadığından CSRF koruması kapalıdır; cookie/refresh değişikliği bu sözleşme ve CSRF korumasıyla birlikte ele alınmalıdır.
- JSON yanıtları `status`, `message`, `data` ve `timestamp` alanlarını içerir. `status`, HTTP durum kodudur. `204 No Content` yanıtlarında gövde yoktur.
- CORS için `http://localhost:5173` ve `http://127.0.0.1:5173` izinlidir.

## API uçları

Tüm API uçları `/api` önekini kullanır. `{id}` değerleri UUID'dir. `from`, `to` ve `startsAt` alanlarını ISO-8601 biçiminde, saat dilimi/UTC offset belirterek gönderin.

### Kayıt ve giriş

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `POST /api/auth/register` | Herkese açık | Kullanıcı kaydeder; başarılı yanıt `201` döner. |
| `POST /api/auth/login` | Herkese açık | JWT erişim token'ı döndürür. |

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
| `POST /api/owner/places/{placeId}/offerings` | Hizmet veya ürün ekler. `SERVICE` için pozitif `durationMinutes` gerekir; `PRODUCT` için süre gönderilmez. |
| `PUT /api/owner/places/{placeId}/offerings/{id}` | Hizmet veya ürünü günceller. |
| `DELETE /api/owner/places/{placeId}/offerings/{id}` | Hizmet veya ürünü devre dışı bırakır. |
| `POST /api/owner/places/{placeId}/business-hours` | Haftanın günü için çalışma aralığı ekler. |
| `DELETE /api/owner/places/{placeId}/business-hours/{id}` | Çalışma aralığını siler. |
| `GET /api/owner/appointments?placeId={uuid}&from={instant}&to={instant}` | Sahibin mekân ve zaman aralığına göre randevularını listeler. |

### Randevu ve bekleme listesi

| Yöntem ve uç | Erişim | Açıklama |
|---|---|---|
| `POST /api/appointments` | `CUSTOMER` | `placeId`, `offeringId` ve gelecekteki `startsAt` ile randevu oluşturur. Bitiş hizmet süresinden hesaplanır. |
| `GET /api/appointments/me` | `CUSTOMER` | Müşterinin kendi randevularını listeler. |
| `DELETE /api/appointments/{id}` | Randevu sahibi veya mekân sahibi | Randevuyu iptal eder; iptal edilmiş randevu kapasiteyi tüketmez. |
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

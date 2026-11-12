# Slotly API CLI Test Report

**Tarih:** 2026-11-11
**Sonuç:** Uygulamanın 25 API rotası CLI ile sınandı; her rotanın temel başarılı akışı beklenen HTTP durumunu verdi. Ek yetki ve hata senaryoları da denendi.

## Test ortamı

- PostgreSQL 17.11 sağlıklıydı. Mevcut uygulamanın 8080 portu dolu olduğu için, kaynak kod bu klasörden Maven ile 8081 portunda ve yalnız bu kontrol için açılan ayrı PostgreSQL veritabanında çalıştırıldı.
- Test sonunda uygulama durduruldu ve geçici veritabanı kaldırıldı. Test kullanıcıları ve mekânlar kalıcı yerel veritabanına yazılmadı.
- `mvnw.cmd` PowerShell’de `Cannot index into a null array` hatası verdi. Maven 3.10.0 yerel cache’den, çevrimdışı modda çalıştırıldı.
- İstekler Windows CLI’dan `curl.exe` ile gönderildi. PowerShell’in yerel komutlara JSON tırnaklarını aktarma biçimi nedeniyle ilk body denemesi bozuk JSON oldu; `HttpMessageNotReadableException` verdi. JSON gövdesini geçici dosyadan `curl.exe --data-binary @dosya.json` ile gönderince istekler doğru biçimde işlendi. Bu test düzeneği hatasıydı, uygulama hatası değildi.

## Rota sonuçları

| Grup | Yöntem ve rota | Temel sonuç |
|---|---|---:|
| Kimlik | `POST /api/auth/register` | 201 |
| Kimlik | `POST /api/auth/login` | 200 |
| Mekân | `GET /api/places` | 200 |
| Mekân | `GET /api/places/{id}` | 200 aktif mekân; 404 devre dışı mekân |
| Mekân | `GET /api/owner/places` | 200 OWNER; 401 oturumsuz; 403 CUSTOMER |
| Mekân | `POST /api/owner/places` | 201 OWNER; 403 CUSTOMER |
| Mekân | `PUT /api/owner/places/{id}` | 200 |
| Mekân | `DELETE /api/owner/places/{id}` | 204; mekânı devre dışı bırakır |
| Hizmet/ürün | `GET /api/places/{id}/offerings` | 200 |
| Hizmet/ürün | `POST /api/owner/places/{placeId}/offerings` | 201 |
| Hizmet/ürün | `PUT /api/owner/places/{placeId}/offerings/{id}` | 200 |
| Hizmet/ürün | `DELETE /api/owner/places/{placeId}/offerings/{id}` | 204; kaydı devre dışı bırakır |
| Çalışma saati | `GET /api/places/{id}/business-hours` | 200 |
| Çalışma saati | `POST /api/owner/places/{placeId}/business-hours` | 201 |
| Çalışma saati | `DELETE /api/owner/places/{placeId}/business-hours/{id}` | 204 |
| Uygunluk | `GET /api/places/{id}/availability?offeringId=&from=&to=` | 200 |
| Randevu | `POST /api/appointments` | 201 |
| Randevu | `GET /api/appointments/me` | 200 CUSTOMER; 401 oturumsuz; 403 OWNER |
| Randevu | `DELETE /api/appointments/{id}` | 204 |
| Randevu | `GET /api/owner/appointments?placeId=&from=&to=` | 200 OWNER |
| Bekleme listesi | `GET /api/waitlist/me` | 200 CUSTOMER; 401 oturumsuz |
| Bekleme listesi | `POST /api/waitlist` | 201 |
| Bekleme listesi | `POST /api/waitlist/{id}/accept` | 200 aktif teklif |
| Bekleme listesi | `POST /api/waitlist/{id}/decline` | 200 aktif teklif |
| Bekleme listesi | `DELETE /api/waitlist/{id}` | 204 |

## Hata ve yetki kontrolleri

- Tekrarlanan kullanıcı adı `409 Username already exists`; hatalı parola `401 Invalid credentials` verdi.
- Müşteri dolu kapasiteye randevu almaya çalışınca `409 Place has no capacity for this time` döndü.
- Aynı müşteri ve zaman için ikinci bekleme kaydı `409 You are already waiting for this time` verdi.
- Çakışan çalışma aralığı `409 Business hour overlaps an existing interval` döndürdü.
- Ters zaman aralığıyla uygunluk isteği `400 Availability range must be positive and at most 31 days` verdi.
- Başka müşterinin randevusunu iptal etme ve yanlış rolün OWNER/CUSTOMER uçlarını çağırması `403` döndürdü.
- İptal edilmiş randevuyu tekrar iptal etme ve kapanmış bekleme teklifini kabul etme `409` döndürdü.
- Randevu iptali sonrası bekleyen kayda teklif açılması, teklifin kabulü ve reddi, bekleme kaydından ayrılma akışları çalıştı.

## Actuator

- `GET /actuator/health`: kimlik doğrulamasız `200`.
- `GET /actuator/info`: kimlik doğrulamasız `401`, geçerli JWT ile `200`. Bu davranış mevcut `SecurityConfig` ile uyumlu: yalnız health ucu açıkça anonim erişime izinli.

## GitHub bağlantısı

- Bu çalışma klasöründe `.git` yok; `git rev-parse` ve `git remote` bu nedenle çalışmıyor. Yerel klasör bir Git çalışma ağacı olarak bağlı değil.
- Codex’in GitHub entegrasyonu `muhsinlight` hesabı için bağlı ve 26 erişilebilir depo listelendi. Erişilebilir depolar arasında `Slotly` eşleşmesi bulunmadı.
- Sonuç: GitHub hesabı entegrasyonu var; bu Slotly klasörünün GitHub deposuna yerel remote bağlantısı yok ve entegrasyonda Slotly adlı erişilebilir repo görünmüyor.

## CLI örneği

```powershell
$payload = @{ username = 'ornek'; password = 'OrnekSifre!2026'; firstName = 'Ada'; lastName = 'Yilmaz'; role = 'CUSTOMER' }
$bodyPath = Join-Path $env:TEMP 'slotly-request.json'
[System.IO.File]::WriteAllText($bodyPath, ($payload | ConvertTo-Json -Compress), [System.Text.UTF8Encoding]::new($false))
curl.exe -i -X POST -H 'Content-Type: application/json' --data-binary "@$bodyPath" http://localhost:8081/api/auth/register
Remove-Item -LiteralPath $bodyPath
```

> Bu tarihli manuel kontrol kaydı güncel CI sonucunun yerine geçmez. Tekrarlanabilir kontroller için [geliştirme rehberine](../DEVELOPMENT.md) bakın.

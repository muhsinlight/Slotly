# Slotly — proje talimatları

## Proje mimarisi
- Ürün: randevu ve otomatik bekleme listesi yönetimi.
- Backend: Spring Boot 4.1.2, Java 25, Maven, JPA ve PostgreSQL.
- Frontend: Vue 3, TypeScript ve Vite.
- Yerel PostgreSQL: `compose.yaml` içinde PostgreSQL 17.
- Katmanlar arası davranış ve API sözleşmelerini değiştirirken frontend, backend ve veritabanı etkilerini birlikte değerlendir; mevcut teknoloji seçimlerini gerekçe olmadan değiştirme.

## Hızlı gezinme
- Backend Java kaynakları: `backend/src/main/java/com/slotly/api/`; ortak altyapı `common/`, user, auth, place, booking ve waitlist alanları `module/` içindedir. Her alan kendi katmanlarını içerir. Tek Maven projesi kullanılır; app veya alt POM yoktur.
- Backend ayarları ve migration'lar: `backend/src/main/resources/`.
- Backend testleri: `backend/src/test/`; komutlar için `backend/mvnw.cmd` ve `backend/pom.xml`.
- Frontend kaynakları: `frontend/src/`; ana ekran `App.vue`, tekrar kullanılan arayüz parçaları `components/` altında.
- Yardımcı komutlar: `scripts/`; demo ve test Compose dosyaları: `docker/`. Yerel push kontrolü: Git dışındaki `scripts/check.ps1`.
- API ve alan davranışlarının yaşayan özeti: `README.md`; yerel PostgreSQL tanımı: `compose.yaml`.
- İşe başlarken yalnızca ilgili akışın kaynak ve testlerini aç; dosya yolları değişmiş olabileceği için bu haritayı kaynak kodun yerine kullanma.

## Çalışma kuralları
- Önce ilgili kodu, yapılandırmayı ve bu depodaki değişiklikleri incele; mevcut kullanıcı değişikliklerini koru.
- Mevcut yapıya uygun, kapsamı sınırlı çözümler seç. Ürün akışı veya alan kuralı belirsizse etkilenen kodu değiştirmeden önce sor.
- Java kodunu tutarlı girintile ve okunaklı biçimlendir: alanları, anotasyonları ve metotları sıkıştırıp aynı satıra yığma; metot gövdelerini ve atamaları ayrı satırlarda yaz. Yakındaki `User.java` gibi mevcut biçimi izle.
- Kod değişikliklerinde ilgili doğrulamaları çalıştır: backend için Maven Wrapper, frontend için `npm` scriptleri. PostgreSQL gerektiren kontrollerde veritabanının çalışır olup olmadığını belirt.
- Tamamlanan ve doğrulanan her bağımsız işi Conventional Commits biçiminde commit et; bitmiş değişiklikleri commit edilmeden bırakma.
- Sonuçta değişen davranışı, çalıştırılan kontrolleri ve varsa çözülemeyen engelleri kısa biçimde raporla.

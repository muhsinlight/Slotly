# Slotly — proje talimatları

Randevu ve otomatik bekleme listesi yönetimi. Backend: Java/Spring Boot, Maven, JPA/PostgreSQL; frontend: Vue/TypeScript/Vite. Güncel sürümler: `backend/pom.xml`, `frontend/package.json`, `compose.yaml`.

## Kaynaklar
- Alan/API kuralları: `README.md`, `docs/API.md`; geliştirme ve doğrulama: `docs/DEVELOPMENT.md`.
- Backend: `backend/src/main/java/com/slotly/api/` (`common/`, `module/`); ayar/migration: `backend/src/main/resources/`; testler: `backend/src/test/`.
- Frontend: `frontend/src/`; yardımcı komutlar: `scripts/`; test/demo ortamları: `docker/`.

## Çalışma kuralları
- Önce Git durumunu ve ilgili kaynak/testleri incele; kullanıcı değişikliklerini koru. Mevcut mimariye uygun, sınırlı değişiklik yap; teknoloji seçimlerini gerekçesiz değiştirme.
- API veya alan davranışı değişirken frontend, backend ve veritabanı etkilerini birlikte değerlendir. Ürün kuralı belirsizse etkilenen kodu değiştirmeden önce sor.
- Projenin okunaklı biçimini koru; Java alanlarını, anotasyonlarını, metotlarını ve atamalarını tek satıra sıkıştırma.
- Kod değişikliğinde ilgili Maven Wrapper/npm kontrollerini çalıştır; PostgreSQL gerektiren kontrollerde veritabanı durumunu belirt. Push öncesi yerel `scripts/check.ps1` kullan; ayrıntılar `docs/DEVELOPMENT.md` içinde.
- Geçici çıktıları terminalde tut; dosya gerekirse Git dışındaki `tmp/verification/` altında sabit işlem adıyla üzerine yaz. Adlar ve uygulama loglarından ayrımı için `docs/DEVELOPMENT.md` dosyasını izle.
- Doğrulanan bağımsız işi Conventional Commits biçiminde commit et. Sonuçta davranış değişikliğini, kontrolleri ve açık engelleri kısa raporla.

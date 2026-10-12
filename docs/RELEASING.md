# Yeni sürüm yayınlama

1. `app/build.gradle.kts` içinde `versionName` ve `versionCode` artırılır ve `main`'e birleştirilir.
2. `versionName` ile aynı adda bir etiketle sürüm yayınlanır, örneğin `v1.2.3`. Üç yoldan biri:
   - Actions → "Android" → "Run workflow" → `main`, "versionName ile GitHub Release yayınla" işaretli (etiket otomatik oluşur)
   - GitHub'da Releases → "Draft a new release" → etiket `v1.2.3` → "Publish release"
   - ya da komut satırından: `git tag v1.2.3 && git push origin v1.2.3`
3. GitHub Actions APK'yı derler, imzalar ve `adimsayar-1.2.3.apk` olarak Release'e ekler. Etiket `versionName` ile uyuşmazsa iş durur.

Release APK, depoda bulunmayan ve GitHub Actions Secret'larında saklanan bir anahtarla imzalanır (`ADIMSAYAR_KEYSTORE_BASE64`, `ADIMSAYAR_KEYSTORE_PASSWORD`). Bu Secret'lar olmadan APK geçici bir debug anahtarıyla imzalanır.

APK dosya adı (`adimsayar-x.y.z.apk`) ve paket adı değiştirilmez; uygulama içi güncelleyici bunlara bağlıdır.

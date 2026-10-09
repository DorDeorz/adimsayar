# AdımSayar

Telefon için adım sayma uygulaması.

Android uygulaması. Tüm veriler cihazda tutulur; hesap, sunucu, senkronizasyon ve ağ izni yoktur.

Günlük adım toplamları tek gerçek kaynaktır. Seviyeler ve madalyalar bu veriden türetilir ve hiçbir yerde saklanmaz.

Adım verisi yalnızca telefonun kendi sensörlerinden okunur. Health Connect ve giyilebilir cihaz verileri kullanılmaz.

Test cihazları: Redmi Note 12 Pro 4G (HyperOS 1 / Android 12, birincil) ve Galaxy S22 (ikincil).

## Proje durumu

v1: ana ekran (bugün, son 7 gün, toplam), iki widget, marka bazlı pil yardım kartı.

- Hedefler ve ölçüm planı: [GOALS.md](GOALS.md)
- Claude Code talimatları: [CLAUDE.md](CLAUDE.md)

## Kurulum

APK GitHub Actions'ta derlenir. Bir PR veya `main` çalıştırmasının "adimsayar-apk-ve-raporlar" çıktısından `app-release.apk` (debug anahtarıyla imzalı) indirilip telefona kurulur.

Yerelde derlemek için Android SDK ve JDK 21 gerekir:

```
./gradlew assembleDebug
```

## Çalıştırma

İlk açılışta "Fiziksel etkinlik" izni verilmelidir. Sayaç ilk okumada yalnızca başlangıç değeri alır; adımlar bu andan sonra sayılır.

## Widget'lar

1. Günün adım sayısı
2. 7 günlük tablo

## Katkı

Bu proje Claude Code ile geliştirilmektedir.
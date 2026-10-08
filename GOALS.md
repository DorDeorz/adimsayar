# Hedefler

Bu dosya projenin hedeflerini ve ölçüm planını tanımlar. Teknik talimatlar `CLAUDE.md` içindedir.

## Ana hedef

> AdımSayar, günde birkaç kez açıldığında anında ve doğru sayıyı gösterir; kendisi pili yiyen bir arka plan servisi çalıştırmaz.

Bir pedometre uygulamasında "performans" CPU benchmark skoru değildir. Kullanıcı üç şeyi hisseder: sayı yanlış geldiğinde, pil bir günde bittiğinde ve açılışta beklediğinde. Performans burada batarya + doğruluk + açılış hızı demektir.

## Test cihazı

**Redmi Note 12 Pro 4G** — Snapdragon 732G (8 nm), MIUI 13 / Android 12, 5000 mAh.

Uygulama bu cihazda geliştirilir ve günlük hayatta kullanılır. Ölçümler bu cihazda yapılır.

MIUI arka plan işlerini agresif öldürür. Topluluklarda "adım sayacı 0'da takılıyor, sadece yeniden başlatınca düzeliyor" raporları yaygındır. Bu bir pil değil **veri doğruluğu** sorunudur ve kabul edilemez.

## Ölçülebilir metrikler

| Metrik | Hedef | Not |
|---|---|---|
| Pil tüketimi | < %2/gün | En önemli metrik |
| Soğuk açılış → sayı görünür | < 700 ms | Uygulama açılıştan sayıyı göstermeye |
| Widget güncelleme gecikmesi | < 30 dk | Kesinlik beklenmez, hedeftir |
| Canlı sayaç gecikmesi | < 2 sn | Uygulama açıkken |
| Doğruluk (vs Mi Fitness) | 5000 adımda ±%3 | Referans olarak sistem pedometresi |
| Kök neden olmayan kayıp | 0'a düşme kabul edilemez | Yeniden başlatma gerektirmemeli |
| Crash-free oturum | > %99,5 | Temel kalite |
| APK boyutu | < 20 MB | İndirme eşiği |

### Ölçüm nasıl yapılır

- Ölçümler **gerçek cihazda** yapılır, emülatörde değil. Emülatörün sensör verisi ve CPU süreleri gerçeği yansıtmaz.
- Aynı senaryo tekrarlanarak ölçülür (soğuk açılış için en az 10 tekrar).
- Pil tüketimi: uygulama aktifken 24 saat boyunca pil yüzdesi farkı, baz olarak cihazın kendi tüketimiyle karşılaştırılarak.
- Doğruluk: Mi Fitness (veya benzeri referans uygulama) ile eşzamanlı, en az 3 ayrı günde 5000+ adımlık yürüyüş.
- Widget gecikmesi: widget'ın gösterdiği değer ile uygulamanın gösterdiği değer arasındaki farkın zaman farkı.

## Widget hedefleri

İki widget:

1. **Günün adımı** — bugünün toplam adımı ve hedef ilerlemesi
2. **7 günlük tablo** — son 7 günün günlük adım sayıları

### Mimari

İki widget **paylaşılan günlük snapshot** okur. İkinci widget için ayrı sensör okuması yoktur; 6 geçmiş gün diskte durur, okumak maliyetsizdir. İki ayrı sensör işi kurmak aynı veriyi iki kez okumak demektir.

| | Günün adımı widget | 7 günlük tablo widget |
|---|---|---|
| Veri kaynağı | Snapshot + hedef | Snapshot + DB'deki 6 geçmiş gün |
| Sensör okuması | Paylaşılan (tek yer) | Yok |
| Günde gerçek değişim | ~30 dk'da bir | Gün dönümünde 1 kez |
| Maliyet | Asıl maliyet burada | Sadece render, ihmal edilebilir |

Gün dönümü widget'ların tek kritik olayıdır: gece yarısı sayacın arşivlenmesi. `JobScheduler` bırakılırsa "15:00 civarında olsun" gibi kayabilir, `AlarmManager` ile tam 00:00'da tetiklenmelidir.

Android `updatePeriodMillis` değerini en fazla 30 dakikaya kısar ve yine de garanti değildir (Doze'da geciktirir, batarya korumasında erteler). "Tam 30 dakikada bir" diye planlama.

## Veri ve senkronizasyon

**Tüm veriler cihazda kalır. Hesap, sunucu ve senkronizasyon yoktur.**

Kapsam dışı: kullanıcı sistemi, giriş ekranı, token yönetimi, backend, ağ izni.

### Veri kaybı koruması

Kullanıcının tek gerçek ihtiyacı atılan adım sayılarını kaybetmemektir. Bunu sunucu olmadan çözeriz:

| Yöntem | Ne zaman çalışır | Not |
|---|---|---|
| Dışa/içe aktarma (JSON) | Kullanıcı istediğinde | Tam kontrol, dosya saklar, unutulma riski var |
| Google Otomatik Yedekleme | Arka planda, otomatik | Ücretsiz, ~25 MB kota |

`android:allowBackup="true"` ile açılır. Cihaz çalınsa bile Google hesabı üzerinden geri gelir.

Xiaomi Cloud'a güvenilmez. MIUI üçüncü parti uygulama verisini yedekler ama güvenilirliği düşüktür, kapatılabilir ve geri yüklemesi sorunludur. Birincil yöntem Google Otomatik Yedekleme'dir.

### Gelecek yön

Arkadaşlarla yarış gibi sosyal özellikler ileride istenirse hesap + sunucu gerekir. Bunun için veri modeli şimdiden şu şekilde kurgulanır:

1. Günlük toplamlar tek gerçek kaynak olsun, türetilmiş hiçbir şey veritabanında tutulmasın
2. Madalya kuralları kodda olsun, tabloda satır olarak değil
3. Günlük kayıtlar tarihe göre indeksli ve eklemeli (append-only) olsun

## Seviye ve madalya sistemi

Madalyalar **kosmetiktir** ve **türetilmiş veridir**. Hiçbir yerde saklanmaz, her açılışta adım geçmişinden hesaplanır.

```
Günlük adım toplamları  = tek gerçek kaynak (saklanır)
Seviyeler              = f(toplam adım)              → türetilir
Madalyalar             = f(günlük adım geçmişi)      → türetilir
```

Kullanıcı kararı: madalya kuralları değişirse yeni kurala uymayan madalyalar geri alınır. Madalyalar dondurulmaz. Yani:

- Toplam adıma göre belirlenen madalyalar kullanıcının adım geçmişine göre hesaplanır
- Bir madalyanın gerektirdiği adım sayısı değişirse, madalya eklenebilir veya kaldırılabilir
- Kullanıcı bir madalyayı kaybedebilir; bu kabul edilmiş davranıştır

### Sonuçlar

- Madalyalar hiçbir koşulda veri kaybına yol açmaz, çünkü saklanacak bir şey yoktur
- İleride sosyal özellik gelirse veri modeli sunucuya taşımaya hazırdır

### UI kuralları

- Geri alınan bir madalyayı nötr göster ("kaldırıldı" gibi bir bildirim yerine sessizce güncelle). Kullanıcıyı kazanımla aynı heyecanla değil, sakin bir bilgiyle karşılaştır.
- Yeniden hesaplama sırasında bildirim üretme. Madalya eşikleri değiştiğinde geçmişe dönük bildirim gönderme, kullanıcıyı kalabalıklaştırır.
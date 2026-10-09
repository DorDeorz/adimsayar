# Hedefler

Bu dosya projenin hedeflerini ve ölçüm planını tanımlar. Teknik talimatlar `CLAUDE.md` içindedir.

## Ana hedef

> AdımSayar, günde birkaç kez açıldığında anında ve doğru sayıyı gösterir; kendisi pili yiyen bir arka plan servisi çalıştırmaz.

Bir pedometre uygulamasında "performans" CPU benchmark skoru değildir. Kullanıcı üç şeyi hisseder: sayı yanlış geldiğinde, pil bir günde bittiğinde ve açılışta beklediğinde. Performans burada batarya + doğruluk + açılış hızı demektir.

## Test cihazları

**Birincil — Redmi Note 12 Pro 4G** — Snapdragon 732G (8 nm), HyperOS 1 / Android 12, 5000 mAh.

Uygulama bu cihazda geliştirilir ve günlük hayatta kullanılır. Ölçümlerin ana kaynağı budur.

HyperOS arka plan işlerini agresif öldürür. Topluluklarda "adım sayacı 0'da takılıyor, sadece yeniden başlatınca düzeliyor" raporları yaygındır. Bu bir pil değil **veri doğruluğu** sorunudur ve kabul edilemez.

**İkincil — Galaxy S22** — Snapdragon 8 Gen 1 veya Exynos 2200, Android 16'ya kadar güncelleme alır.

İkincil cihazın amacı tek sayı üretmek değil, **farklı bir arka plan yönetimi ve sensör davranışını test etmek**. HyperOS ve One UI ikisi de saldırgan ROM'dur ama farklı yollardan. Birinde çalışıyorsa diğerinde de çalışıyor sayılma.

### Kapsam

Uygulama tüm Android telefonlarda çalışacak şekilde tasarlanır. Test cihazları bunu doğrulamak için ikisidir, sınırlamak için değil.

| Cihaz | İşlemci | Android | Ana zorluk |
|---|---|---|---|
| Redmi Note 12 Pro 4G | Snapdragon 732G | 12 / HyperOS 1 | Arka plan öldürme |
| Galaxy S22 | SD 8 Gen 1 / Exynos 2200 | 16'ya kadar | Sensör donması |

İki cihaz bilinçli olarak zıt iki uçtur: biri eski Android ve saldırgan ROM, diğeri güncel Android ve katı bildirim/FGS kuralları.

### Cihaz tespiti

Cihazın üreticisine göre davranış değişebilir. Samsung ve Xiaomi'ye özgü yollar varsayım olarak değil, açıkça sınanmalıdır. Üretici tespiti yapılacaksa `Build.MANUFACTURER` kullanılmalı, model adına göre kırpmaya çalışılmamalıdır.

## Ölçülebilir metrikler

| Metrik | Hedef | Not |
|---|---|---|
| Pil tüketimi | < %2/gün | En önemli metrik |
| Soğuk açılış → sayı görünür | < 700 ms | Uygulama açılıştan sayıyı göstermeye |
| Widget güncelleme gecikmesi | < 30 dk | Kesinlik beklenmez, hedeftir |
| Canlı sayaç gecikmesi | < 2 sn | Uygulama açıkken |
| Doğruluk (vs Mi Fitness / Samsung Health) | 5000 adımda ±%3 | Referans olarak sistem pedometresi |
| Kök neden olmayan kayıp | 0'a düşme kabul edilemez | Yeniden başlatma gerektirmemeli |
| Sensör donması toleransı | Kullanıcı müdahalesi gerekmesin | Bkz. aşağıdaki bölüm |
| Crash-free oturum | > %99,5 | Ağ ve analitik olmadığı için Play Console yoksa ölçülemez; yerel crash log ile izlenir |
| APK boyutu | < 20 MB | İndirme eşiği |

### Sensör donması

Bazı cihazlarda `TYPE_STEP_COUNTER` saymayı durdurur ve kendiliğinden tekrar başlamaz. Samsung cihazlarda S4'ten beri raporlanmıştır: uygulama 121 adımda takılı kalırken cihazın kendi sağlık uygulaması 305 gösterir. Bazı Samsung modellerinde sensör hiç bulunmaz, `getDefaultSensor()` `null` döner. One UI güncellemeleri çalışan bir sensörü bozabilir (Galaxy Z Flip, Android 13 geçişinde olduğu gibi).

Bu kabul edilemez bir durumdur: kullanıcı sayaç donduğunda fark etmeli ve uygulama kendini toparlamalı, cihazı yeniden başlatmak zorunda kalmamalı.

Bu nedenle **tek sensöre güvenilmez, doğrulama yapılır.** `TYPE_STEP_COUNTER` ve `TYPE_STEP_DETECTOR` birbirini kontrol eder; biri sürekli geride kalıyorsa sensör donmuş demektir ve uygulama bunu kullanıcıya bildirir.

Uygulama arka planda sensörü sürekli dinlemediği için donma tespiti yalnızca uygulama açıkken yapılır.

Yeni cihazda ilk hafta boyunca donma olayları kaydedilmeli, toparlanma süresi ölçülmelidir. Hedef: fark edilen her donmada kullanıcı müdahalesi gerekmeden toparlanma.

### Ölçüm nasıl yapılır

- Ölçümler **gerçek cihazda** yapılır, emülatörde değil. Emülatörün sensör verisi ve CPU süreleri gerçeği yansıtmaz.
- Aynı senaryo tekrarlanarak ölçülür (soğuk açılış için en az 10 tekrar).
- Pil tüketimi: uygulama aktifken 24 saat boyunca pil yüzdesi farkı, baz olarak cihazın kendi tüketimiyle karşılaştırılarak.
- Doğruluk: Mi Fitness (veya benzeri referans uygulama) ile eşzamanlı, en az 3 ayrı günde 5000+ adımlık yürüyüş.
- Widget gecikmesi: widget'ın gösterdiği değer ile uygulamanın gösterdiği değer arasındaki farkın zaman farkı.

### Kapalıyken sayma testi

Uygulama arka planda sensörü dinlemez, sayacı seyrek okur ve farkı kaydeder (bkz. `CLAUDE.md` → "Seyrek okuma ve fark modeli"). Bunun çalışması sayacın uygulama kapalıyken de saymasına bağlıdır. Her test cihazında:

1. Uygulamayı açıp sayıyı not et, sonra son uygulamalardan kapat.
2. Elle sayarak 500 adım yürü.
3. Uygulamayı aç ve farkı kontrol et.

Fark 500'e ±%3 içinde değilse kalıcı bildirimli foreground service, ayarlardan açılabilen bir seçenek olarak eklenir.

## Veri kaynakları

**Karar: Health Connect kullanılmayacak. Veri yalnızca telefonun kendi sensörlerinden okunur.**

Bu karar şu gerekçeyle verildi:

Health Connect, telefon ve giyilebilir cihaz verilerini birleştirir. Kullanıcı kolunda saat taşırken telefon da sayıyorsa adım iki kez yazılır. Samsung kullanıcılarında bu yaygın bir şikâyettir. Ayrıca Health Connect'in kaynak öncelik sırasını yalnızca kullanıcı değiştirebilir ve bu sıra okunabilir bir API ile alınamaz. Sonuç olarak **aynı cihaz, aynı gün, hiçbir veri değişikliği olmadan iki farklı toplam üretilebilir.**

Bu, uygulamanın temel vaadiyle çelişir. Doğru ama nadiren eksik veri, çoğu zaman doğru ama bazen iki katı olan veriden yeğdir. Üstelik türetilmiş madalyalar geçmişe dönük uygulandığı için, temelindeki sayının değişken olması madalyaların da geriye dönük değişmesine yol açar.

Bu kararın bedeli kabul edilmiştir: Samsung Health aktarımına güvenilerek sensör donmasının kolayca aşılması artık mümkün değildir. Bunun yerine donma kendi tespit edilip kullanıcıya bildirilir.

İleride giyilebilir cihaz desteği istenirse Health Connect eklenebilir, ancak o zaman aralık bazlı deduplikasyon birlikte eklenmelidir. Temel yapıda yer almaz.

### Kullanılacak sensörler

| Sensör | Rol |
|---|---|
| `TYPE_STEP_COUNTER` | Birincil kaynak, kümülatif, genelde en doğru. Donabilir |
| `TYPE_STEP_DETECTOR` | Çapraz doğrulama. Her adımda olay üretir, donmaya duyarlı değil |

Her iki sensör de yoksa uygulama çökmez, anlaşılır bir mesaj gösterir.

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

Xiaomi Cloud'a güvenilmez. HyperOS üçüncü parti uygulama verisini yedekler ama güvenilirliği düşüktür, kapatılabilir ve geri yüklemesi sorunludur. Birincil yöntem Google Otomatik Yedekleme'dir.

### Gelecek yön

Arkadaşlarla yarış gibi sosyal özellikler ileride istenirse hesap + sunucu gerekir. Bunun için veri modeli şimdiden şu şekilde kurgulanır:

1. Günlük toplamlar tek gerçek kaynak olsun, türetilmiş hiçbir şey veritabanında tutulmasın
2. Madalya kuralları kodda olsun, tabloda satır olarak değil
3. Günlük kayıtlar tarihe göre indeksli olsun (gün başına tek satır, geçmiş günler değişmez)

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
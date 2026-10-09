# CLAUDE.md

Bu dosya Claude Code için proje talimatlarıdır. Kod yazmaya başlamadan önce oku.

Hedefler ve ölçüm planı `GOALS.md` içindedir. Orada tanımlı metrikler varsa kod bunlara göre yazılır.

## Proje

AdımSayar — telefon için adım sayma uygulaması. Tüm Android telefonları hedeflenir.

Uygulama öncelikle geliştiricinin kendi Redmi Note 12 Pro 4G'si için yapılır; bu cihazda resmi bir adım sayar yoktur. Play Store yayını öncelik değildir. Çekirdek özellikler: günün adımı widget'ı, 7 günlük tablo widget'ı, uygulamada geçmiş ve toplam.

Test cihazları: **Redmi Note 12 Pro 4G** (SD 732G, HyperOS 1 / Android 12, birincil) ve **Galaxy S22** (SD 8 Gen 1 / Exynos 2200, Android 16'ya kadar, ikincil).

İki cihaz farklı üreticilerin arka plan yönetimini test etmek içindir (HyperOS ve One UI). Tek cihazda yapılan test yeterli sayılmaz.

## Durum

v1 kodu yazıldı: `app/` altında tek modül, paket `com.dordeorz.adimsayar`.

Kararlaştırılmış ve değiştirilmemesi gerekenler:

- Framework: native Kotlin, Jetpack Compose + Material3. Widget'lar Glance ile. `minSdk 23`.
- Kalıcı depolama: Room.
- Arka planda sayma: kalıcı bildirimli servis yoktur. Sayaç seyrek okunur ve fark kaydedilir (bkz. "Adım sayma").
- Xiaomi'de HyperOS adım kaydı, kullanıcının açtığı anahtarla ana kaynak olabilir (2026-10-09 kararı).
- Veriler sadece cihazda tutulur. Hesap, sunucu, senkronizasyon ve ağ izni yoktur.
- **Health Connect kullanılmaz.** Veri yalnızca telefonun kendi sensörlerinden okunur. `androidx.health` bağımlılığı ekleme.
- Madalyalar ve seviyeler türetilmiş veridir, hiçbir yerde saklanmaz. Kurallar değişirse geriye dönük uygulanır. Hesaplama `data/Achievements.kt` içindedir; günlük ve haftalık hedef kullanıcı ayarıdır, değişince geçmiş günler de yeni hedefe göre değerlendirilir. Erken kalkan madalyası saatlik veri gerektirdiği için yalnızca HyperOS kaydı açıkken görünür ve o kayıttan hesaplanır.
- Arayüz Türkçe (varsayılan, `values/`) ve 11 dil daha içerir (`values-xx/`, liste `SUPPORTED_LANGUAGES`). Dil seçimi Ayarlar'dadır; "Sistem" desteklenmeyen bir dilde İngilizceye düşer. Widget ve bildirimler de seçilen dili kullanır (`Locales.kt`).
- Ölçümler gerçek cihazda yapılır, emülatörde değil.
- Uygulama tüm Android telefonları hedefler. Xiaomi'ye özgü bir çözüm varsayılan yapılmaz, üretici tespitiyle seçilen bir yol olarak uygulanır.

## Kaynak çokluğu

Tek sensöre güvenilmez. İki kaynak okunur ve çapraz doğrulanır:

| Kaynak | Güçlü yanı | Zayıf yanı |
|---|---|---|
| `TYPE_STEP_DETECTOR` | Çapraz doğrulama, donmaya duyarlı değil | Yavaşlatma/hızlanma sırasında hata |
| `TYPE_STEP_COUNTER` | Birincil kaynak, genelde en doğru | **Donabiliyor** |

Health Connect kararı `GOALS.md` içinde gerekçesiyle tanımlıdır. Gerekçenin özeti: Health Connect telefon ve giyilebilir cihaz verisini birleştirir, kolunda saat varken adım iki kez yazılır. Ayrıca kaynak öncelik sırasını yalnızca kullanıcı değiştirebilir ve okunabilir bir API yoktur, dolayısıyla aynı gün iki farklı toplam üretilebilir. Bu, uygulamanın temel vaadiyle ve türetilmiş veri ilkesiyle çelişir.

Giyilebilir cihaz desteği ileride istenirse Health Connect eklenebilir, ancak aralık bazlı deduplikasyonla birlikte eklenmelidir.

### Kritik: sensörün null dönmesi

Bazı cihazlarda `getDefaultSensor(TYPE_STEP_COUNTER)` `null` döner. Cihaz `FEATURE_SENSOR_STEP_COUNTER` bildiriyor olsa bile sensör bulunmayabilir (Galaxy A7 2018 gibi). Bu durumda uygulama çökmemeli, anlaşılır bir mesaj göstermeli.

### Kritik: sayacın donması

`TYPE_STEP_COUNTER` saymayı durdurabilir ve kendiliğinden devam etmeyebilir. Samsung cihazlarda yıllardır raporlanıyor (uygulama 121 adımda takılırken Samsung Health 305 gösterir). One UI güncellemeleri çalışan sensörü bozabilir — Galaxy Z Flip Android 13 geçişinde sensörü kaybettiği raporlanmıştır.

Kural: **tek ölçüme güvenme, doğrula.** `TYPE_STEP_DETECTOR` olay üretmeye devam ederken sayaç hiç ilerlemiyorsa donmuş demektir. Bu durumda kullanıcıya bildir ve yeniden kayıt dene. Kullanıcı cihazı yeniden başlatmak zorunda kalmamalıdır.

Donma kuralı bir pencere ve eşikle tanımlanır (ör. 2 dakikada detector ≥ 50 adım, counter 0), çünkü iki sensörün olayları farklı zamanlarda teslim edilebilir. İki sensörü karşılaştırmak sürekli dinleme gerektirdiği için donma tespiti yalnızca uygulama açıkken yapılır. Bu bilinen bir trade-off'tur.

## Üreticiye özgü davranış

Üretici tespiti `Build.MANUFACTURER` ile yapılır. Model adına göre kırpmaya çalışma.

### Xiaomi / HyperOS

Xiaomi'nin kendi adım servisi vardır: `miui.util.FeatureParser.getBoolean("support_steps_provider", false)` ile destek kontrolü, ardından `content://` üzerinden sorgu. Yürüyüş/koşu ayrımı da verir (mod 0 = desteklenmiyor, 2 = yürüyüş, 3 = koşu). API adı `miui` olsa da HyperOS'ta da çalışır.

Bu bir varsayılan yol **değildir**. Standart sensörler temel kaynaktır; Xiaomi'ye özgü yol yalnızca üretici tespitiyle ve kullanıcı Ayarlar'daki "HyperOS adım kaydını kullan" anahtarını açtığında kullanılır (`data/XiaomiSteps.kt`). Açıkken günlük toplamlar HyperOS kaydından gelir, sensör okumaları toplamlara eklenmez. Gerekçe ve test sonuçları `GOALS.md` → "Xiaomi / HyperOS adım kaydı". Okuma izni `miui.permission.READ_STEPS` manifest'te tanımlıdır.

Redmi'de `TYPE_STEP_COUNTER` yalnızca bir uygulama kayıtlıyken sayar; HyperOS'un kendi servisi yalnızca detector'ı açık tutar. Bu cihazda bildirimsiz sayım ancak HyperOS kaydıyla mümkündür.

HyperOS arka plan işlerini agresif öldürür. Yeniden başlatma gerektiren kayıp bir gün kabul edilemezdir. `BOOT_COMPLETED` ve saat değişimi sonrası yeniden başlatma gerekebilir.

### Samsung / One UI

`registerListener` başarısız olabilir (`registerListener fail (1) :: 17, SAMSUNG Step Counter Sensor`). Bu izin eksikliğinden kaynaklanır — `ACTIVITY_RECOGNITION` manifest'te tanımlı olmalı ve runtime'da istenmelidir.

## Adım sayma

### Seyrek okuma ve fark modeli

`TYPE_STEP_COUNTER` cihaz açıldığından beri kümülatif adım sayısını verir. Uygulama sensörü sürekli dinlemez; sayacı seyrek okur ve son okumaya göre farkı kaydeder:

- Uygulama açıldığında (açıkken canlı sayaç için kayıtlı kalır)
- Widget güncellenirken
- `JobScheduler` ile periyodik arka plan okuması (~15 dk)
- `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` sonrası

Her okumada son sayaç değeri ve `elapsedRealtimeNanos` saklanır. Yeni değer öncekinden küçükse cihaz yeniden başlamıştır; yeni değer olduğu gibi fark kabul edilir.

Bilinen trade-off'lar:

- Android belgelerine göre hiçbir uygulama sensörü dinlemiyorsa sayaç saymayabilir. Pratikte birçok cihazda sistem servisi sensörü açık tutar, ama bu varsayılmaz, ölçülür (bkz. `GOALS.md` → "Kapalıyken sayma testi").
- Gün sınırını geçen bir aralığın adımları iki güne en fazla bir okuma aralığı hatayla bölünür.
- Kapanmadan önceki son okumadan sonra atılan adımlar yeniden başlatmada kaybolabilir.

Yedek yol: kullanıcının Ayarlar'dan açabileceği, sessiz kanalda kalıcı bildirimli foreground service (`StepCounterService`, Android 14+ için `foregroundServiceType="health"`). Sayacı `maxReportLatency` 60 sn ile kayıtlı tutar. Varsayılan kapalıdır. Redmi'deki ilk 500 adım testi başarısız olduğu için eklendi.

### Davranış tablosu

| Durum | Davranış |
|---|---|
| Uygulama açık | Sensörler kayıtlı, canlı sayaç, donma kontrolü |
| Uygulama arka planda | Kayıt yok, periyodik okuma ve fark |
| Widget güncellemesi | Aynı fark modeliyle okuma |

Uygulama açıkken pil için batch'leme kullanılabilir (`registerListener` 4 parametreli sürüm, `maxReportLatency`). `sensor.getFifoMaxEventCount()` `0` dönüyorsa cihaz batch'lemeyi desteklemiyor demektir.

### İzinler

- API 29+ (Android 10) runtime izni: `ACTIVITY_RECOGNITION`. İki sensör için de zorunlu.
- `HIGH_SAMPLING_RATE_SENSORS` gereksiz, ekleme.
- Ağ izni (`INTERNET`) ekleme. Bu uygulama çevrimdışıdır.

## Widget'lar

Dört widget: günün adımı, 7 günlük tablo, seri ve hafta, aylık takvim. Renkler `GlanceTheme` ile gelir: "Telefonun renklerini kullan" açıksa Material You, değilse uygulamanın teması. Hepsi `SizeMode.Exact` ile boyuta göre düzen değiştirir.

Kurallar:

- Hepsi **paylaşılan günlük snapshot** okur. Widget başına ayrı sensör okuması yoktur.
- Gün dönümü `AlarmManager` ile 00:00'da tetiklenir. `JobScheduler` bırakılırsa gün dönümü "15:00 civarında" gibi kayabilir. Android 14+'ta tam zamanlı alarm izni varsayılan kapalıdır; o cihazlarda birkaç dakika kayan `setAndAllowWhileIdle` yeterlidir, çünkü adımların güne bölünmesi alarmdan değil okuma zamanından yapılır.
- `updatePeriodMillis` en fazla 30 dakikaya kırpılır ve yine de garanti değildir. Kesinlik beklenmemeli.
- Widget çizimi ana ekrandaki verinin kopyası değil, aynı kaynaktan okunmalıdır.

### Widget'lar her cihazda

Widget güncelleme davranışı üreticiden bağımsız olmalıdır. HyperOS ve One UI widget arka plan güncellemelerini farklı biçimde kısıtlar; ikisinde de `AlarmManager` gün dönümü ve `JobScheduler` periyodik okuma birlikte çalışmalıdır.

## Veri

Günlük adım toplamları tek gerçek kaynaktır. Türetilmiş hiçbir şey veritabanında saklanmaz.

Kalıcılık: Room'da `date` birincil anahtarlı günlük toplam tablosu. Gün içinde toplam güncellenir (upsert). Bir gün, gece yarısını geçen ilk okumayla tamamlanır; sonrasında değiştirilmez.

Veri kaybına karşı iki katman:

- `android:allowBackup="true"` — Google Otomatik Yedekleme, ücretsiz. Son sayaç değeri ve okuma zamanı yedekten **hariç tutulur** (`fullBackupContent` ve API 31+ için `dataExtractionRules`); yoksa yeni cihaza geri yüklemede ilk fark yanlış hesaplanır.
- Dışa/içe aktarma — kullanıcı kontrolünde dosya

Xiaomi Cloud'a güvenilmez.

## Genel kurallar

- Kullanıcının seçtiği stack dışına çıkma; yeni bağımlılık eklemeden önce mevcut çözümü kullan.
- Yorum satırı yazma; kod kendini açıklasın.
- Türkçe arayüz metinlerini kullanıcıya sormadan değiştirme.
- Ağ izni, hesap ekranı, analitik veya reklam SDK'sı ekleme.
- Kararlaştırılmış bir maddeyi değiştirmek gerekiyorsa önce kullanıcıya sor ve `GOALS.md` ile `CLAUDE.md`'yi birlikte güncelle.

## Pil optimizasyonu yardımı

Saldırgan ROM'larda arka plan öldürme gerçek ve kaçınılmazdır. HyperOS ve One UI bir uygulamayı ekran kapandıktan 1-2 dakika sonra öldürebiliyor. Bu uygulama hatası değildir ama veri kaybına yol açıyorsa kullanıcıya yol gösterilmelidir.

Kural: **muafiyeti zorla isteme, ama yolunu göster.**

- Marka bazlı adımlar kullanıcıya sunulur, uygulama ayarları değiştirmez
- Yardım kartı marka agresifse ve muafiyet verilmemişse görünür
- Muafiyet verildiğinde (`onResume`'da okunarak) kart bir daha çıkmaz
- Stock Android'de yalnızca nötr tek adım gösterilir
- Kart kapatılabilir olmalı, kullanıcıyı zorlamamalı

## Ölçüm disiplini

Kronometre projesinden alınan ilke: **ölçülmeyen hiçbir şey kabul edilmez.**

- "Hafif", "optimize", "verimli" gibi sıfatlar tek başına geçer; ölçümle desteklenmedikçe kullanılmaz
- Her trade-off açıkça belgelenir. Belgelenmemiş trade-off, sonradan "optimize etmedin" diye cezalandırılacak gizli maliyettir
- Öncelik sırası: **doğruluk → güvenilirlik → pil → güzellik → kapsam**
- Hedef sıfır değil, ölçülebilir taban çizgisi. Boşta kullanımdan ayırt edilemeyen bir taban

## Basitlik kazanır

Kronometre'de uygulanmış ve işe yarayan kural:

- DI kütüphanesi, soyutlama katmanı, generic repository, çok modüllü yapı: gerekçeleri yoksa ekleme
- Bir şeyi 10 satırda çözebiliyorsan 10 satırda çöz
- Emin değilsen tahmin etme. Derlemesini bilmediğin bir API'yi kullanma, tahmini sürüm yazma. `TODO` bırakıp sor

## Kronometre projesinden referans

`DorDeorz/Kronometre` bu projenin kardeşidir ve aynı iki cihazda test edilmiştir. Yeniden kullanılabilecek hazır parçalar:

- `data/OemProfile.kt` — 8 marka için hazır tablo (Xiaomi, Huawei, Oppo, Vivo, Samsung, Asus, Transsion, stock). `hyperos` marker'ı zaten içeriyor
- `ui/oem/BatteryOptimizationHelper.kt` — marka bazlı derin linkler ve fallback zinciri
- Glance tabanlı widget, Compose + Material3 kurulumu, `minSdk 23` kararı
- `.github/workflows/android.yml` — CI kurulumu

Bu dosyalar kopyalanmadan önce okunmalı, doğrudan kopyalanmamalıdır.
# CLAUDE.md

Bu dosya Claude Code için projet talimatlarıdır. Kod yazmaya başlamadan önce oku.

Hedefler ve ölçüm planı `GOALS.md` içindedir. Orada tanımlı metrikler varsa kod bunlara göre yazılır.

## Proje

AdımSayar — telefon için adım sayma uygulaması. Tüm Android telefonları hedeflenir.

Test cihazları: **Redmi Note 12 Pro 4G** (SD 732G, HyperOS 1 / Android 12, birincil) ve **Galaxy S22** (SD 8 Gen 1 / Exynos 2200, Android 16'ya kadar, ikincil).

İki cihaz farklı üreticilerin arka plan yönetimini test etmek içindir (HyperOS ve One UI). Tek cihazda yapılan test yeterli sayılmaz.

## Durum

Repo boş bir iskelettir. Şunlar henüz kararlaştırılmamıştır ve bunları **kullanıcıya sor, kendin karar verme**:

- Framework seçimi (native Kotlin, Flutter, React Native/Expo vb.)
- Kalıcı depolama teknolojisi
- Grafik/UI kütüphanesi

Kararlaştırılmış ve değiştirilmemesi gerekenler:

- Veriler sadece cihazda tutulur. Hesap, sunucu, senkronizasyon ve ağ izni yoktur.
- **Health Connect kullanılmaz.** Veri yalnızca telefonun kendi sensörlerinden okunur. `androidx.health` bağımlılığı ekleme.
- Madalyalar ve seviyeler türetilmiş veridir, hiçbir yerde saklanmaz. Kurallar değişirse geriye dönük uygulanır.
- Ölçümler gerçek cihazda yapılır, emülatörde değil.
- Uygulama tüm Android telefonları hedefler. Xiaomi'ye özgü bir çözüm varsayılan yapılmaz, üretici tespitiyle seçilen bir yol olarak uygulanır.

## Kaynak çokluğu

Tek sensöre güvenilmez. Üç kaynak okunur ve çapraz doğrulanır:

| Kaynak | Güçlü yanı | Zayıf yanı |
|---|---|---|
| `TYPE_STEP_DETECTOR` | Çapraz doğrulama, donmaya duyarlı değil | Yavaşlatma/hızlanma sırasında hata |
| `TYPE_STEP_COUNTER` | Birincil kaynak, genelde en doğru | **Dongelebiliyor** |

**Health Connect kullanılmayacaktır.** Bu karar `GOALS.md` içinde gerekçesiyle tanımlıdır. `androidx.health` bağımlılığı ekleme. Sağlık uygulamalarından veri okumayan tek veri kaynağı telefonun kendi sensörleridir.

Gerekçenin özeti: Health Connect telefon ve giyilebilir cihaz verisini birleştirir, kolunda saat varken adım iki kez yazılır. Ayrıca kaynak öncelik sırasını yalnızca kullanıcı değiştirebilir ve okunabilir bir API yoktur, dolayısıyla aynı gün iki farklı toplam üretilebilir. Bu, uygulamanın temel vaadiyle ve türetilmiş veri ilkesiyle çelişir.

Giyilebilir cihaz desteği ileride istenirse Health Connect eklenebilir, ancak aralık bazlı deduplikasyonla birlikte eklenmelidir.

### Kritik: sensörün null dönmesi

Bazı cihazlarda `getDefaultSensor(TYPE_STEP_COUNTER)` `null` döner. Cihaz `FEATURE_SENSOR_STEP_COUNTER` bildiriyor olsa bile sensör bulunmayabilir (Galaxy A7 2018 gibi). Bu durumda uygulama çökmemeli, anlaşılır bir mesaj göstermeli.

### Kritik: sayacın donması

`TYPE_STEP_COUNTER` saymayı durdurabilir ve kendiliğinden devam etmeyebilir. Samsung cihazlarda yıllardır raporlanıyor (uygulama 121 adımda takılırken Samsung Health 305 gösterir). One UI güncellemeleri çalışan sensörü bozabilir — Galaxy Z Flip Android 13 geçişinde sensörü kaybettiği raporlanmıştır.

Kural: **tek ölçüme güvenme, doğrula.** `TYPE_STEP_DETECTOR` olay üretmeye devam ederken sayaç hiç ilerlemiyorsa donmuş demektir. Bu durumda kullanıcıya bildir ve yeniden kayıt dene. Kullanıcı cihazı yeniden başlatmak zorunda kalmamalıdır.

## Üreticiye özgü davranış

Üretici tespiti `Build.MANUFACTURER` ile yapılır. Model adına göre kırpmaya çalışma.

### Xiaomi / HyperOS

Xiaomi'nin kendi adım servisi vardır: `miui.util.FeatureParser.getBoolean("support_steps_provider", false)` ile destek kontrolü, ardından `content://` üzerinden sorgu. Yürüyüş/koşu ayrımı da verir (mod 0 = desteklenmiyor, 2 = yürüyüş, 3 = koşu). API adı `miui` olsa da HyperOS'ta da çalışır.

Bu bir varsayılan yol **değildir**. Standart sensörler temel kaynaktır; Xiaomi'ye özgü yol yalnızca üretici tespitiyle ve açıkça seçildiğinde kullanılır. Health Connect yerine geçmez, yalnızca ek bir doğrulama kaynağı olabilir.

HyperOS arka plan işlerini agresif öldürür. Yeniden başlatma gerektiren kayıp bir gün kabul edilemezdir. `BOOT_COMPLETED` ve saat değişimi sonrası yeniden başlatma gerekebilir.

### Samsung / One UI

`registerListener` başarısız olabilir (`registerListener fail (1) :: 17, SAMSUNG Step Counter Sensor`). Bu izin eksikliğinden kaynaklanır — `ACTIVITY_RECOGNITION` manifest'te tanımlı olmalı ve runtime'da istenmelidir.

## Adım sayma

### Kritik kural: sensör kaydı açık kalmalı

`Sensor.TYPE_STEP_COUNTER` **yalnızca kayıtlı olduğu sürece sayar.** Kaydı kaldırırsan o dönemde atılan adımlar hiçbir yerde okunamaz ve telafisi yoktur.

Bu yüzden "pil için kaydı kaldır" bir optimizasyon değil, **ürün kararıdır** ve varsayılan olarak yapılmamalıdır.

Pil tasarrufu kaydetmeyi bırakmaktan değil, **donanım batch'lemesinden** gelir:

```kotlin
sensorManager.registerListener(
    listener,
    stepSensor,
    SensorManager.SENSOR_DELAY_UI,   // örnekleme hızı
    60_000_000L                      // maxReportLatency: 60 sn
)
```

`maxReportLatency` pozitif verildiğinde olaylar donanım FIFO'sunda birikir ve işlemci uyandırılmaz. Pil tasarrufunun asıl kaynağı budur.

Bu, cihazınızda gerçekten çalıştığını doğrulamak icin `sensor.getFifoMaxEventCount()` kontrol edilebilir. `0` dönüyorsa cihaz batch modunu desteklemiyor demektir.

### Davranış tablosu

| Durum | Davranış |
|---|---|
| Uygulama açık | Sensör kayıtlı, canlı sayaç |
| Uygulama arka planda | **Kayıt açık kalır**, `maxReportLatency` ile toplu teslim |
| Widget güncellemesi | `JobScheduler` ile seyrek okuma, aralık olabildiğince uzun |

Google'ın önerisi: seyrek okuma yapacaksan aralığı "olabildiğince uzun" tut, gerçek zamanlı veriye ihtiyacın yoksa.

### İzinler

- API 29+ (Android 10) runtime izni: `ACTIVITY_RECOGNITION`. İki sensör için de zorunlu.
- `HIGH_SAMPLING_RATE_SENSORS` gereksiz, ekleme.
- Ağ izni (`INTERNET`) ekleme. Bu uygulama çevrimdışıdır.

## Widget'lar

İki widget: günün adımı, ve 7 günlük tablo.

Kurallar:

- İkisi de **paylaşılan günlük snapshot** okur. İkinci widget için ayrı sensör okuması yoktur.
- Gün dönümü `AlarmManager` ile tam 00:00'da tetiklenir. `JobScheduler` bırakılırsa gün dönümü "15:00 civarında" gibi kayabilir.
- `updatePeriodMillis` en fazla 30 dakikaya kırpılır ve yine de garanti değildir. Kesinlik beklenmemeli.
- Widget çizimi ana ekrandaki verinin kopyası değil, aynı kaynaktan okunmalıdır.

### Widget'lar her cihazda

Widget güncelleme davranışı üreticiden bağımsız olmalıdır. HyperOS ve One UI widget arka plan güncellemelerini farklı biçimde kısıtlar; ikisinde de `AlarmManager` gün dönümü ve `JobScheduler` periyodik okuma birlikte çalışmalıdır.

## Veri

Günlük adım toplamları tek gerçek kaynaktır. Türetilmiş hiçbir şey veritabanında saklanmaz.

Kalıcılık: günlük kayıtlar tarihe göre indeksli ve eklemeli (append-only) olsun. Bu, ileride sunucuya taşımak (varsa) ucuz kılar.

Veri kaybına karşı iki katman:

- `android:allowBackup="true"` — Google Otomatik Yedekleme, ücretsiz
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
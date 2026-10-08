# CLAUDE.md

Bu dosya Claude Code için projet talimatlarıdır. Kod yazmaya başlamadan önce oku.

Hedefler ve ölçüm planı `GOALS.md` içindedir. Orada tanımlı metrikler varsa kod bunlara göre yazılır.

## Proje

AdımSayar — telefon için adım sayma uygulaması. Android'de geliştirilir, Redmi Note 12 Pro 4G (Snapdragon 732G, MIUI 13 / Android 12) üzerinde test edilir.

## Durum

Repo boş bir iskelettir. Şunlar henüz kararlaştırılmamıştır ve bunları **kullanıcıya sor, kendin karar verme**:

- Framework seçimi (native Kotlin, Flutter, React Native/Expo vb.)
- Kalıcı depolama teknolojisi
- Grafik/UI kütüphanesi

Kararlaştırılmış ve değiştirilmemesi gerekenler:

- Veriler sadece cihazda tutulur. Hesap, sunucu, senkronizasyon ve ağ izni yoktur.
- Madalyalar ve seviyeler türetilmiş veridir, hiçbir yerde saklanmaz. Kurallar değişirse geriye dönük uygulanır.
- Ölçümler gerçek cihazda yapılır, emülatörde değil.

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

## MIUI ve Xiaomi'ye özgü

### Adım verisi kaynakları

İki kaynak var ve doğrulukları farklı:

1. `Sensor.TYPE_STEP_COUNTER` — standart Android, donanım sayacı
2. Xiaomi'nin kendi adım servisi — `miui.util.FeatureParser.getBoolean("support_steps_provider", false)` ile destek kontrolü edilir, ardından `content://` üzerinden sorgulanır. Yürüyüş/koşu ayrımı da verir (mod 0 = desteklenmiyor, 2 = yürüyüş, 3 = koşu).

Kaynak 2 daha güvenilir olabilir ama cihaz dışına çıkmaz ve MIUI'a bağımlıdır. Hangisinin kullanılacağı **kullanıcıya sor**.

### Arka plan kısıtları

MIUI arka plan işlerini agresif öldürür. Topluluklarda "adım sayacı 0'da takılıyor, sadece yeniden başlatınca düzeliyor" raporları yaygındır.

- Yeniden başlatma gerektiren kayıp bir gün, metrikte küçük ama kullanıcıya kabul edilemezdir. `GOALS.md`'de bu ayrı bir metrik olarak tanımlıdır.
- `BOOT_COMPLETED` ve saat değişimi sonrası yeniden başlatma gerekebilir.
- Xiaomi pil optimizasyonu muafiyeti istenmemeli; doğru desen gereksiz pil israfı yapmaz. Eğer gerekirse kullanıcıya nedenini açıkla.

## Widget'lar

İki widget: günün adımı, ve 7 günlük tablo.

Kurallar:

- İkisi de **paylaşılan günlük snapshot** okur. İkinci widget için ayrı sensör okuması yoktur.
- Gün dönümü `AlarmManager` ile tam 00:00'da tetiklenir. `JobScheduler` bırakılırsa gün dönümü "15:00 civarında" gibi kayabilir.
- `updatePeriodMillis` en fazla 30 dakikaya kırpılır ve yine de garanti değildir. Kesinlik beklenmemeli.
- Widget çizimi ana ekrandaki verinin kopyası değil, aynı kaynaktan okunmalıdır.

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
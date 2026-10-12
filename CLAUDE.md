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
- Veriler sadece cihazda tutulur. Hesap, sunucu ve senkronizasyon yoktur. `INTERNET` izni yalnızca kullanıcının başlattığı güncelleme kontrolü ve indirmesi içindir (bkz. "İzinler").
- **Health Connect yalnızca bileklik verisi için, kullanıcının açtığı anahtarla okunur** (2026-10-11 kararı). Varsayılan kapalıdır. Yalnızca okuma yapılır, Health Connect'e hiçbir şey yazılmaz. Kapalıyken veri yalnızca telefonun kendi sensörlerinden (ve açıksa HyperOS kaydından) gelir. Bağımlılık `androidx.health.connect:connect-client`. Adımlar `data/WearableSteps.kt`; egzersiz, mesafe, aktif kalori, nabız, uyku (evreleriyle) ve kan oksijeni `data/WearableHealth.kt` içinde; Geçmiş'te seçilen gün ve Sağlık sayfasındaki 7/30 günlük özet için anlık okunur ve hiçbir yerde saklanmaz.
- Madalyalar ve seviyeler adım geçmişinden hesaplanır (`data/Achievements.kt`). Kazanılan madalya kalıcıdır (2026-10-12 kararı): ilk kazanıldığı tarih `medals` tablosuna yazılır ve hedef değişince kaybolmaz (`data/Keeps.kt`). Seviye, "Nx" sayısı ve ilerleme türetilmiş kalır. Erken kalkan madalyası saatlik veri gerektirdiği için yalnızca HyperOS kaydı açıkken görünür ve o kayıttan hesaplanır.
- Seri jokeri: ayda 2 hak, `streak_freezes` tablosu. Jokerli gün seriyi bozmaz, uzatmaz, hedef günü sayılmaz.
- Haftalık görevler saklanmaz; haftanın başlangıç tarihiyle tohumlanır, o haftadan önceki 28 güne göre seçilir (`data/Challenges.kt`).
- Sanal rota: seçilen rota ve başlangıç günü ayarlarda saklanır, ilerleme o günden bu yana adımlardan (boya göre km) hesaplanır (`data/Routes.kt`). Rota mesafeleri yaklaşıktır, harita koordinatlardan çevrimdışı çizilir.
- Kuş avatarı türetilmiştir, saklanmaz (`data/Avatar.kt`, çizim `ui/BirdPainter.kt`): evre seviyeden, ruh hali bugünkü ve dünkü adımdan, eşyalar madalyalardan gelir.
- Arayüz Türkçe (varsayılan, `values/`) ve 11 dil daha içerir (`values-xx/`, liste `SUPPORTED_LANGUAGES`). Dil seçimi Ayarlar'dadır; "Sistem" desteklenmeyen bir dilde İngilizceye düşer. Widget ve bildirimler de seçilen dili kullanır (`Locales.kt`).
- Ölçümler gerçek cihazda yapılır, emülatörde değil.
- Uygulama tüm Android telefonları hedefler. Xiaomi'ye özgü bir çözüm varsayılan yapılmaz, üretici tespitiyle seçilen bir yol olarak uygulanır.

## Kaynak çokluğu

Tek sensöre güvenilmez. İki kaynak okunur ve çapraz doğrulanır:

| Kaynak | Güçlü yanı | Zayıf yanı |
|---|---|---|
| `TYPE_STEP_DETECTOR` | Çapraz doğrulama, donmaya duyarlı değil | Yavaşlatma/hızlanma sırasında hata |
| `TYPE_STEP_COUNTER` | Birincil kaynak, genelde en doğru | **Donabiliyor** |

Health Connect kararı `GOALS.md` içinde gerekçesiyle tanımlıdır ("Bileklik adımları"). Özet: Health Connect'in tüm kaynakları birleştiren toplamı kullanılmaz, çünkü telefon ve bileklik adımlarını kullanıcının değiştirebildiği bir öncelik sırasıyla birleştirir. Bunun yerine her kaynak uygulama için ayrı günlük toplam istenir (`aggregateGroupByPeriod` + `dataOriginFilter`). Ham kayıtlar sayfa sayfa okunmaz: Mi Fitness dakikalık kayıt yazdığı için bu Health Connect'in istek sınırını aşıyordu (2026-10-11, Redmi'de "Rate limited"). Arka planda okumalar en az 5 dakika arayla yapılır, hata sonrası 1 saat beklenir. Uygulama açıkken dakikada bir okunur, hata sonrası 5 dakika beklenir; her okuma ikisini de erteler, böylece aynı dakikada iki okuma olmaz; arka plandaki bir hata uygulama açıkken yapılan okumayı bekletmez (2026-10-12: gece Mi Fitness 175 yazmışken uygulama 70'te kaldı).

- Her saat için kaynak uygulamaların (ör. Mi Fitness) sayılarından en büyüğü alınır; farklı uygulamaların adımları toplanmaz.
- Saat saat birleştirilir: bilekliğin adım yazdığı saatlerde bileklik, yazmadığı saatlerde telefon sayılır. Bileklik telefondan daha doğru sayar, bu yüzden telefonun daha yüksek olması bilekliğin hatası sayılmaz; bileklik takılı olmayan saatler de telefondan gelir (kullanıcı kararları, 2026-10-11). Telefonun saatlik verisi yalnızca HyperOS kaydı açıkken vardır; yoksa gün bazında bileklik ile telefonun büyüğü alınır.
- Bileklik toplamları ayrı tabloda (`band_steps`) tutulur, telefon toplamları (`daily_steps`) değişmez. Anahtar kapatılınca bileklik tablosu silinir ve telefonun sayıları geri gelir.

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

Android 9+ sensörlere yalnızca etkin (ön planda ya da ön plan servisinde) uygulamalar erişebilir; arka plandaki işte `TYPE_STEP_COUNTER` olay vermez. Galaxy S22'de (2026-10-11) 15 dk işinin ve widget'ın tüm okumaları "sensör yanıt vermedi" ile bitti, logda `SensorService: resetSensorStateForIdleUID` görüldü. Bu yüzden iş, sayacı kısa bir ön plan servisi (`ReadOnceService`, `health` tipi, en fazla 10 sn) içinde okur. Android 12+ bu servisi arka plandan yalnızca pil optimizasyonu muafiyeti olan uygulamaya başlatır; muafiyet yoksa okuma eski yoldan denenir. Bildirim ertelenir (`FOREGROUND_SERVICE_DEFERRED`), servis 10 sn dolmadan bittiği için çoğu zaman görünmez. Bu kalıcı bildirimli servis değildir.

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
| Uygulama arka planda | Kayıt yok, periyodik okuma (kısa ön plan servisinde) ve fark |
| Widget güncellemesi | Aynı fark modeliyle okuma |

Uygulama açıkken pil için batch'leme kullanılabilir (`registerListener` 4 parametreli sürüm, `maxReportLatency`). `sensor.getFifoMaxEventCount()` `0` dönüyorsa cihaz batch'lemeyi desteklemiyor demektir.

### İzinler

- API 29+ (Android 10) runtime izni: `ACTIVITY_RECOGNITION`. İki sensör için de zorunlu.
- `HIGH_SAMPLING_RATE_SENSORS` gereksiz, ekleme.
- `INTERNET` yalnızca güncelleme kontrolü için vardır. Tek istisna Ayarlar'daki güncelleme kontrolüdür (2026-10-09 kararı): yalnızca kullanıcı düğmeye bastığında GitHub'ın son sürüm bilgisini (`api.github.com/repos/DorDeorz/adimsayar/releases/latest`) okur. Yeni sürüm varsa kullanıcı ikinci kez dokununca o sürümün APK'sı GitHub Releases'tan indirilir ve Android'in kurulum ekranı açılır (2026-10-12 kararı; `REQUEST_INSTALL_PACKAGES`, `FileProvider`, `data/UpdateInstaller.kt`). Kurulum kullanıcı onayıyla Android tarafından yapılır, uygulama sessiz kurulum yapmaz. Adım verisi ya da kişisel bilgi gönderilmez, arka planda kontrol ya da indirme yapılmaz. Başka hiçbir ağ isteği eklenmez.

## Widget'lar

Sekiz widget: günün adımı, 7 günlük tablo, seri ve hafta, aylık takvim, saat saat bugün, ısı haritası, uyku ve nabız, kuş. Glance satır/sütunları en fazla 10 çocuk aldığı için 24 saatlik çubuklar ve ısı haritası beyaz bitmap olarak çizilip `ColorFilter.tint` ile boyanır. Uyku ve nabız widget'ı Health Connect'i en fazla 15 dakikada bir okur. Renkler `GlanceTheme` ile gelir: "Telefonun renklerini kullan" açıksa Material You, değilse uygulamanın teması. Hepsi `SizeMode.Exact` ile boyuta göre düzen değiştirir.

Kurallar:

- Adım widget'ları **paylaşılan günlük snapshot** okur. Widget başına ayrı sensör okuması yoktur.
- Gün dönümü `AlarmManager` ile 00:00'da tetiklenir. `JobScheduler` bırakılırsa gün dönümü "15:00 civarında" gibi kayabilir. Android 14+'ta tam zamanlı alarm izni varsayılan kapalıdır; o cihazlarda birkaç dakika kayan `setAndAllowWhileIdle` yeterlidir, çünkü adımların güne bölünmesi alarmdan değil okuma zamanından yapılır.
- `updatePeriodMillis` en fazla 30 dakikaya kırpılır ve yine de garanti değildir. Kesinlik beklenmemeli.
- Widget çizimi ana ekrandaki verinin kopyası değil, aynı kaynaktan okunmalıdır.

### Widget'lar her cihazda

Widget güncelleme davranışı üreticiden bağımsız olmalıdır. HyperOS ve One UI widget arka plan güncellemelerini farklı biçimde kısıtlar; ikisinde de `AlarmManager` gün dönümü ve `JobScheduler` periyodik okuma birlikte çalışmalıdır.

## Veri

Günlük adım toplamları tek gerçek kaynaktır. Türetilmiş hiçbir şey veritabanında saklanmaz.

Kullanıcının elle girdiği su miktarı da saklanır (`water` tablosu, gün başına ml; 2026-10-12 kullanıcı isteği). İsteğe bağlı su hatırlatması 09:00–21:00 arasında 1, 2 ya da 3 saatte bir, hedefe ulaşılmadıysa bildirim gönderir; bildirimdeki düğme bir bardak ekler (`SystemEventReceiver.ACTION_WATER_ADD`). Önerilen hedef kilo × 35 ml'dir.

Telefonun sayaç farkları gün tablosuna ek olarak saat saat de yazılır (`hourly_steps`, `StepMath.splitByHour`). Saatlik grafik ve saatlik widget HyperOS kaydı kapalıyken bu tablodan okur; bölme okuma aralığı kadar hatalıdır.

Kullanıcının elle girdiği kilo ölçümleri de saklanır (`weights` tablosu, `date` birincil anahtarlı; 2026-10-12 kullanıcı isteği). Bunlar ölçüm değil kullanıcı girdisidir; vücut kitle endeksi ve kilo değişimi saklanmaz, her seferinde hesaplanır (`data/HealthMath.kt`). Son kilo Ayarlar'daki kilo değerini de günceller.

Kalıcılık: Room'da `date` birincil anahtarlı günlük toplam tablosu. Gün içinde toplam güncellenir (upsert). Bir gün, gece yarısını geçen ilk okumayla tamamlanır; sonrasında değiştirilmez.

Veri kaybına karşı iki katman:

- `android:allowBackup="true"` — Google Otomatik Yedekleme, ücretsiz. Son sayaç değeri ve okuma zamanı yedekten **hariç tutulur** (`fullBackupContent` ve API 31+ için `dataExtractionRules`); yoksa yeni cihaza geri yüklemede ilk fark yanlış hesaplanır.
- Dışa/içe aktarma — kullanıcı kontrolünde dosya. İçe aktarma Samsung Health, Mi Fitness, Mi Fit ve Google Fit dışa aktarımlarını da okur (`data/HistoryImport.kt`)
- Paylaşım kartı: günün özeti cihazda PNG olarak çizilir ve Android paylaşım menüsüyle gönderilir (`ui/ShareCard.kt`); uygulama ağa bir şey göndermez

Xiaomi Cloud'a güvenilmez.

## Genel kurallar

- Kullanıcının seçtiği stack dışına çıkma; yeni bağımlılık eklemeden önce mevcut çözümü kullan.
- Yorum satırı yazma; kod kendini açıklasın.
- Türkçe arayüz metinlerini kullanıcıya sormadan değiştirme.
- Güncelleme kontrolü dışında ağ isteği, hesap ekranı, analitik veya reklam SDK'sı ekleme.
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
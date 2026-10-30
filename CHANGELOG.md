# Sürüm geçmişi

Her sürümde nelerin eklendiği ve nelerin düzeltildiği, yeniden eskiye. Uygulamada da Ayarlar → Sürüm notları altında, seçilen dilde görülebilir. APK'lar: [Releases](https://github.com/DorDeorz/adimsayar/releases).

## 0.20.3

- Bugün sayfası kartları hizalandı: "Günün sözü" ve içeriği kısa kalan diğer kartlar (tatil, izin, uyarı, yürüyüş planı vb.) artık tam genişlikte. "Seri" ve "Bu hafta" kartları yan yana aynı yükseklikte (`IntrinsicSize.Min` + `fillMaxHeight`).

## 0.20.2

- Açılışta ve güncellemeden sonra görülen ANR (uygulama yanıt vermiyor) giderildi. Sebep: `SystemEventReceiver` canlı bileklik bağlantısından eşitleme sonucunu 60 s bekliyordu; bileklik uzaktayken bu, arka plan yayın sınırını (~60 s) aşıyordu.
- `BandLive.syncNow(foreground)`: arka plan çağrısı bileklik bağlı değilken hiç beklemez, bağlıyken en fazla 20 s bekler. Elle "Eşitle" eskisi gibi 60 s bekler.
- `SystemEventReceiver`: açılış, güncelleme ve saat olaylarında yalnızca telefon sayacı okunur (en fazla 15 s), bileklik ve Health Connect ayrı bir işte (`READ_SYSTEM_JOB_ID`, kaynak "Sistem") eşitlenir. Hatırlatma alarmlarında tam okuma en fazla 35 s sürer, sığmazsa yine işe devredilir; hatırlatma kontrolleri her durumda çalışır.
- `StepUpdater.refresh`: telefon sayacı kaydedilip widget'lar bileklikten önce güncellenir, eşitlemeden sonra bir kez daha güncellenir. Parametre adı `sensorTimeoutMs`.
- `ReadOnceService`: işe toplam 150 s sınır, WakeLock bu sınırla uyumlu.
- `SystemEventReceiver` artık dışa kapalı (`exported="false"`): başka uygulamalar su ekleyemez veya hatırlatma tetikleyemez. Sistem yayınları ve uygulamanın kendi alarmları gelmeye devam eder.

## 0.20.1

(0.15.2 için yazılmış ama 0.20.0 birleştirmesine girmemiş eklemeler.)

- Erken kalkan madalyası HyperOS kaydı olmadan da hesaplanır: telefonun saatlik kaydı ve bilekliğin saatleri (bileklik saati önce), 08:00'den önce en az 1.000 adım (`StepMath.earlyBirdDays`).
- Geçmiş günlerde eksik telefon adımları (ör. yeniden başlatma öncesi son okumadan sonraki adımlar) Health Connect'teki telefonun kendi kaydından (veri kaynağı `android`) doldurulur. Sadece son 3 gün, bugün hariç; gün toplamındaki eksik, Health Connect'in önde olduğu saatlere dağıtılır (`PhoneHealthSteps`, `StepMath.phoneGaps`). Uygulama açılınca çalışır, READ_STEPS izni gerekir.
- "Arka planda sürekli say" açıklamasına "çoğu telefonda gerekmez" notu.
- 15 dk işi geç kalınca hemen ikinci kez çalışabiliyordu; 2 dakika içindeki ikinci çalışma atlanır.

## 0.20.0

- Kuş türleri (`BirdSpecies`): çalıkuşu, baykuş (seviye 3), papağan (6), kartal (10). Seçilen tür ve renk uygulamada, Kuş widget'ında, canlı duvar kâğıdında ve kadran tasarımcısının bilekliğe gönderdiği kuş resimlerinde çizilir.
- Tohumlar ve besleme (`Seeds`): her 1.000 adım bir tohum. Kuşu besle (1 tohum, kuş zıplar ve öter), tüy rengi (30–100 tohum) ve ötüş (20–40 tohum) al. Ötüşler `ui/BirdSong.kt` içinde sesten üretilir, ses dosyası yok.
- Aksesuarlar: kazanılanlar kuş penceresinden takılıp çıkarılır; yeni gözlük (42 km), çiçek (7 gün su), uyku başlığı (7 gece uyku), sırt çantası (ilk rota).
- Canlı duvar kâğıdı (`wallpaper/BirdWallpaper.kt`, Ayarlar → Görünüm ve dil): gündüz ve gece göğü; kuş günün adımı hedefe yaklaştıkça yuvadan dala uçar, dakikada bir yenilenir, yalnızca ekrandayken çalışır.
- Yürüyüş araçları kartı (Bugün):
  - Tempo metronomu (`walk/MetronomeService.kt`, dakikada 60–180 adım, bildirimle çalışır).
  - Köpek gezdirme: başlat ve durdur, aradaki adımlar `walks` tablosuna yazılır.
  - GPS ile yürüyüş (`walk/WalkRecordService.kt`): konum izni yalnızca bu düğmeye basınca istenir ve yalnızca kayıt sırasında kullanılır. Mesafe ve rota kaydedilir, kayıtlı yürüyüşlerde rota çizilir. `ACCESS_FINE_LOCATION` artık Android 12 ve üstünde de isteniyor.
- Kat sayacı (`sensor/FloorCounter.kt`): basınç sensörü olan telefonlarda, uygulama açıkken, arka planda sürekli sayma açıkken ve GPS yürüyüşünde çalışır. Adım atılırken iki dakika içinde 3 m yükselme bir kat sayılır; asansör ve hava değişimi sayılmaz. Sensör yoksa hiçbir yerde görünmez.
- Uygulama kilidi: 4–8 haneli PIN (SHA-256 özetiyle saklanır) ve isteğe bağlı parmak izi ya da yüz (Android 9 ve üstü). Uygulama 30 saniyeden uzun arka planda kalınca yeniden kilitlenir.
- Sade görünüm: Bugün sayfasında yalnızca büyük yazıyla adım, hedef ve ilerleme.
- İlk açılış rehberi: yalnızca yeni kurulumda (eski kullanıcılar atlar). Hoş geldin, günlük hedef, boy-kilo ve adım izni sayfaları var.

## 0.19.0

- Hedefe kalan süre (`data/StepPace.kt`): son 14 günün en hareketli saatlerinden kendi yürüme hızın (dakikada 80–125 adım, veri yoksa 100) bulunur; Bugün'de halkanın altında ve geniş Bugün widget'ında "Hedefe ≈ 25 dk yürüyüş kaldı".
- Yemek karşılığı (`data/Foods.kt`): yakılan kalori "≈ 2 × simit" gibi gösterilir (hamburger, simit, çikolata, kola, elma).
- Günün sözü: Bugün'de her gün başka bir söz (12 dilde 15 söz); Ayarlar → Görünüm ve dil'den kapatılabilir.
- Eğilim kartı (Geçmiş): bu ayın ilk günleri geçen ayın aynı günleriyle, son 7 gün önceki 7 günle yüzde olarak karşılaştırılır.
- Aktif saat haritası (Geçmiş): son 8 haftada her gün ve saat için ortalama adım, 7 × 24 kare; en hareketli saat yazılır.
- Yıl sonu hikâyesi: Aralık ve Ocak'ta Geçmiş'in başında, ayrıca her yıl kartında "Hikâyeyi aç"; kaydırmalı sayfalar (toplam adım, yol, en iyi gün ve ay, hedef günleri, madalyalar) ve metin olarak paylaşma.
- Toparlanma puanı (Sağlık): dün geceki uyku önceki haftayla, dinlenme nabzı ortalamayla karşılaştırılır; 0–100.
- Fitness yaşı (Sağlık): dinlenme nabzından Uth formülüyle VO2max, son 30 günün günlük adımıyla küçük düzeltme; yaşa göre ortalamayla karşılaştırılır.
- Yeni madalyalar (`data/ExtraMedals.kt`): mesafe (10–1000 km), su serisi (3–100 gün), uyku serisi (7 saat ve üstü, 3–30 gece), rota bitirme (1–7 rota; bitirilen rotalar artık saklanır).

## 0.18.0

- Sabah özeti (Ayarlar → Bildirimler, saat 6–10 arası seçilir, `background/ExtraReminders.kt`): dünkü adım, bileklik ya da Health Connect varsa gece uykusu ve bugünün hedefi.
- Rekor bildirimi: bugün önceki en iyi günü ya da bu hafta en iyi haftayı geçince bir kez bildirim (en az 7 günlük geçmiş gerekir). 15 dakikalık okumadan sonra bakılır.
- Hareketsizlik hatırlatması: 10:00–20:00 arası her saat başı, önceki saatte 250 adımdan az yürünmüşse bildirim (`DayHours`: telefonun saatlik kaydı, bilekliğin saatleri öncelikli). Tatilde susar.
- Yürüyüş saati planı: haftanın günlerine saat; saatinde bildirim, o saat ya da sonrakinde 1.500 adım varsa Bugün'deki kartta işaretli.
- Ayın raporu bildirimi: her ayın 1'inde 10:00'da, dokununca Geçmiş açılır.
- Bileklikte hedef kutlaması (bileklik doğrudan bağlıyken): hedefe ulaşınca günde bir kez bilekliğe kuşlu bildirim gider, bileklik titrer.
- Uygulama kısayolları (`res/xml/shortcuts.xml`): Su ekle (bir bardak ekler), Bugün, Kaç adım?
- Su kutucuğu (`tile/WaterTile.kt`): bildirim panelinden bir bardak ekler, bugünkü suyu gösterir.
- "Bugün kaç adım attım?" (`StepAnswerActivity`): cevabı gösterir ve sesli okur. Kısayoldan her zaman çalışır; Google Asistan'ın `GET_EXERCISE_OBSERVATION` App Action'ı uygulama Play Store'dan kurulunca devreye girer.

## 0.17.0

- Güne not (`data/DayData.kt`, tablo `day_notes`): Geçmiş'te seçili günün kartında "Not ekle"; en fazla 120 karakter, takvimde günün altında küçük nokta.
- Günü elle düzelt (tablo `day_overrides`): yazılan sayı o günün telefon ve bileklik sayımının yerine geçer (`MERGED` sorgusunda önce gelir); "Sayılana dön" ile silinir.
- Tatil modu (Ayarlar → Hedefler, en fazla 90 gün): seçili günler seri için joker gibi sayılır ama joker harcanmaz (`AppSettings.vacationDays`), seri, yaklaşma, su ve kilo hatırlatmaları susar, Bugün'de tatil kartı görünür.
- Hafta sonu hedefi (`AppSettings.goalOn`): cumartesi ve pazar için ayrı günlük hedef; seri, madalya, takvim, halka, widget ve hatırlatmalar o günün hedefini kullanır.
- Aylık ve yıllık hedef (`data/PeriodGoals.kt`): Bugün'de ilerleme çubuğu, bu tempoyla ulaşılacak gün ya da yetişmek için gereken günlük adım.
- Tam yedek (`data/Backup.kt`, Ayarlar → Veri): bütün tablolar ve uygulama ayarları tek JSON dosyasında; geri yükleme verinin yerine geçer. Bileklik eşleşmesi, adım kaynağı ve kadran ayarları yedeğe girmez.
- Verileri silme (`DataEraser`): bir tarih aralığı ya da her şey (ayarlar kalır).
- CSV dışa aktarma `tarih,adim,su_ml,kilo_kg,uyku_dk` sütunlarını yazar; içe aktarma adım sütununu okumaya devam eder.
- Health Connect'e yazma (`data/HealthWrite.kt`, isteğe bağlı): saatlik adımlar sabit kimlikli `StepsRecord` olarak yazılır, yeniden yazınca üstüne gelir; yarım saatte bir, son 2 gün (ilk açılışta 7 gün). Adımlar Health Connect'ten okunurken çift sayılmasın diye çalışmaz.
- Veritabanı 7. sürüm: `day_notes`, `day_overrides`, `walks`, `floors` tabloları (göç ile, eski veri korunur).

## 0.16.0

- Hızlı ayar kutucuğu (`tile/StepTile.kt`, Android 7+): bildirim panelinde bugünün adımı ve hedef yüzdesi (Android 10+ alt satırda), hedefe ulaşınca kutucuk etkin görünür; dokununca Bugün sekmesi açılır. Panel her açıldığında yeniden okunur.
- Akıllı hedef (Ayarlar → Hedefler, varsayılan kapalı, `data/SmartGoal.kt`): açıksa haftanın ilk günü (gece yarısı alarmı ya da uygulama açılışı) günlük hedef son 28 tam günün ortancasının %10 fazlasına doğru, en fazla %10 değişir, 500'e yuvarlanır, en az 3.000. En az 14 gün adım gerekir. Açıldığı hafta değiştirmez; elle hedef değiştirilince yeni hedeften devam eder. Ayarlar satırı son değişikliği ya da gelecek haftanın önerisini gösterir.
- Uyku ve adım (Sağlık, `data/SleepInsights.kt`): seçili dönemde 7 saatten uzun ve kısa uykudan sonraki günlerin ortalama adımı, ve hedefe ulaşılan / ulaşılmayan günlerin gecesindeki ortalama uyku. Her grupta en az 3 gün gerekir; uyku uyanılan güne yazılır, bugün sayılmaz.
- Aylık PDF raporu (Geçmiş → "<ay> raporu (PDF)", `data/MonthReport.kt`, `data/PdfReport.kt`): Android'in `PdfDocument`'ıyla iki sayfa A4: toplam, ortalama, hedef günleri, en iyi gün, mesafe, kalori, önceki aya göre değişim, su ve kilo (varsa), uyku ve dinlenik nabız ortalaması (bileklik ya da Health Connect açıksa), hedef çizgili günlük grafik, haftalar; ikinci sayfada gün gün adım, mesafe, kalori ve su. Dosya paylaşım/kaydetme ekranıyla kaydedilir, internet kullanılmaz.

## 0.15.2

- Tam zamanlı alarmlar satırı Bildirimler sayfasına taşındı.
- Ayarlar → Adım kaynağı → "Bileklik varken telefonu sayma" (varsayılan kapalı, `WearableSteps.isBandOnly`): açıksa bilekliğin veri gönderdiği günlerde gün toplamı yalnız bileklikten gelir (`StepMath.bandDays`); telefon sadece bilekliğin hiç veri göndermediği günlerde kullanılır.
- Bileklik ve telefon her zaman saat saat birleşir (`StepMath.mergeMixed`): bilekliğin takılı olduğu saatlerde yalnız bileklik, diğer saatlerde telefonun saatlik kaydı. Önceden HyperOS kaydı kapalıyken günün büyük toplamı alınıyordu, telefon fazla sayınca bilekliği eziyordu. Telefonun saatlik kaydı olmayan eski ya da içe aktarılmış günlerde eski kural (günün büyüğü) kalır.
- Özel ROM'lar (crDroid, LineageOS): Xiaomi telefonda MIUI'nin adım kaynağı (`com.miui.providers.steps`) yoksa "Xiaomi adımları" açık kalmış olsa bile kullanılmaz; adımlar telefonun sayacından okunur. Lineage tabanlı ROM'da pil kartı MIUI'nin otomatik başlatma adımlarını değil, yalnızca pil optimizasyonu muafiyetini gösterir.
- Tam zamanlı alarmlar (Ayarlar → Bildirimler): Android 14 ve sonrasında yeni kurulumda bu izin kapalı gelir ve gece yarısı kaydı, hatırlatmalar ve saate göre kadran bir saate kadar gecikebilir. Satır izin ekranını açar; izin verilince alarmlar yeniden kurulur (`Schedules.setAlarm`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`).

## 0.14.1

- Kuşun ruh hali: FitWren kadranlarında ve kadran tasarımında kuş artık adım hedefi yüzdesine bağlı bir resim listesi (`watchface/make_faces.py`, `MOODS`): %0 uykulu, %25 uyanık, %60 neşeli, %100 kutlama (konfeti). Tasarımda "Kuş" seçeneklerine "Hedefe göre" eklendi; "Neşeli" ve "Uykulu" kuşu sabit tutar.
- Kadranda hava durumu: FitWren kadranlarında (Yol hariç) ve tasarımda hava simgesi (bilekliğin hava kodu, kaynak 3031) ve sıcaklık (°C, kaynak 2031). Bileklik hava durumunu FitWren'in gönderdiği bilgiden alır. Tasarımda "Görünüm" sekmesinden gizlenebilir.
- Ekran kapalıyken görünüm: her FitWren kadranının içinde Mi Create'in AOD projesi (`<kadran>/AOD/AOD.fprj`): siyah üstünde sönük saat, tarih ve küçük kuş.
- Tasarım paylaşma: kadran tasarımının "Tema" sekmesinde "Tasarımı paylaş" tasarımı (fotoğrafı JPEG olarak içinde) `fitwren-kadran.json` dosyasına yazar ve paylaşım menüsünü açar; "Tasarım içe al" böyle bir dosyayı açar (`FaceDesigner.export` / `importDesign`).
- Saate göre kadran (Saat kadranları → "Saate göre kadran"): gündüz ve gece kadranı ile başlangıç saatleri seçilir; FitWren o saatlerde aktif kadranı değiştirir (`background/FaceSchedule.kt`, AlarmManager). Bileklik bağlı değilse 10 dakika sonra yeniden dener.
- Mi Band 6 kadranı (Ayarlar → Bileklik → Saat kadranları): Mi Band 6 için yapılmış `.bin` kadran dosyası (`UIHH` başlıklı) bilekliğin ürün yazılımı kanalından gönderilir (`band/HuamiFaceUpload.kt`, Gadgetbridge'in UpdateFirmwareOperation2020'sinden): parça boyu istenir, dosya boyu ve CRC32 bildirilir, dosya bilekliğin bildirdiği yerden parça parça gönderilir. Yer yoksa ya da şarj azsa ayrı uyarı gösterilir.
- Mi Band 5, Mi Band 6 ve Amazfit Band 5'in dakika kayıtları 8 bayt okunur (önceden 4 bayt sayılıyordu, Gadgetbridge ile aynı).

## 0.13.3

- Kadran tasarla (Saat kadranları → "Kadran tasarla", Mi Band 8): hazır renk geçişleri ya da kendi fotoğrafın arka plan olur; saat, dakika, yazı ve hedef çubuğu renkleri ile kuş (neşeli, uykulu, yok) seçilir, önizleme anında güncellenir. Kadran telefonda üretilir: FitWren'in beyaz-siyah şablon kadranı (`assets/designer/template.bin`) açılır, resimleri değiştirilir ve dosya yeniden yazılır (`band/XiaomiFaceFile.kt`; dosya yapısı `watchface/facebin.py` ile derlenmiş kadranlarda doğrulandı). Saat ve dakika için 7 rakam stili (kalın, ince, eğik, tırnaklı, daktilo, içi boş, LCD), saatin yeri (üstte, ortada, altta; şablondaki parçaların y konumu dosyada kaydırılır), 8 hazır tema, arka planı karartma, tarih / adım / hedef çubuğu / nabız ve pil ayrı ayrı gizlenebilir (gizlenen parçanın resimleri saydam yapılır). Tasarım ekranında önizleme üstte sabit durur; seçenekler altında Tema, Saat, Arka plan, Renkler ve Görünüm sekmelerindedir, "Bilekliğe yükle" en altta sabittir, "Geri al" ve "Sıfırla" üstte. Fotoğraf önizlemede parmakla kaydırılır ve iki parmakla büyütülür; tasarım ayarları saklanır. Her tasarım yeni bir kadran numarası alır; yenisi yüklenince bir önceki tasarım bileklikten silinir.
- Başka bir cihaz için yapılmış kadran dosyası seçilince uyarı: dosyadaki önizleme boyutu bilekliğinkine (Mi Band 8: 122 x 310) uymuyorsa "Yine de yükle" sorulur.
- Kadran mağazası (Ayarlar → Bileklik → Saat kadranları → Kadran mağazası, şimdilik Xiaomi Smart Band 8): Xiaomi'nin ücretsiz kadranları ad, resim, kategori ve açıklamayla ızgarada gösterilir; ada göre aranır, kategoriye göre süzülür. "Yükle" dosyayı Xiaomi'den indirir, MD5'ini mağazanın verdiği değerle karşılaştırır ve bilekliğe gönderir. Uygulamada yalnızca kadran numaraları var; ad, resim ve dosya her açılışta Xiaomi'den (`watch-appstore.iot.mi.com`, giriş gerekmez) gelir, her sorgu dört öneri daha getirir. Mağazadan yüklenen kadranın adı ve resmi saklanır ve kadran listesinde görünür.
- FitWren kadranları (Mi Band 8): kuşlu dokuz kadran (FitWren, Gece, Yol, Analog, Neon, Dal, Dal Gece, Halka, Sade) uygulamayla gelir; Saat kadranları → "FitWren kadranları"ndan seçilip yüklenir. Kaynakları `watchface/make_faces.py` (Pillow) ile üretilen Mi Create projeleri; `.bin` dosyaları Windows'ta Mi Create derleyicisiyle derlenip `assets/faces/` altına konur. Dosyası olmayan kadran listede görünmez.

## 0.12.7

- Saat kadranları penceresi yenilendi: dosya seçilince kadranın önizleme resmi (dosyadaki LVGL resmi, RLE v1/v2 açılır) ve adı gösterilir, "Yükle" ile gönderilir. FitWren'den yüklenen kadranların resmi saklanır ve listede görünür (bileklik resim göndermez). Kullanılan kadran renkli, kadranlar "Bileklikle gelen" / "Senin yüklediğin" diye ayrılır, silmeden önce sorulur.
- Bildirim uygulama listesinde uygulama simgeleri, arama kutusu ve kaç uygulamanın açık olduğu; onay kutuları yerine anahtarlar.

## 0.12.6

- Bileklik bildirimleri: "Hangi uygulamalar" ile tek tek uygulamalar kapatılabilir (varsayılan hepsi açık, `BandNotify.blocked`). "Sessiz saatler" (varsayılan 23:00–07:00) boyunca uygulama bildirimleri ve hatırlatmalar bilekliğe gitmez, aramalar yine gelir.
- FitWren hatırlatmaları bileklikte: hedef, seri, haftalık özet, kilo ve su bildirimleri ("hedef" ve "su" kanalları) bilekliğe de gider. Su eklenince yapılan sessiz güncelleme bilekliği yeniden titretmez.
- Bileklik pili (Xiaomi ailesi): bağlanınca ve her eşitlemede pil okunur (komut 2/78, yoksa 2/1). Ayarlar'daki cihaz kartında yüzde ve şarj durumu, yeni "Bileklik pili" widget'ı, %20'nin altında telefonda bir kez uyarı (Ayarlar'dan kapatılabilir; %30'u geçince ya da şarja takılınca yeniden kurulur).
- Telefonumu bul: bileklikteki "Telefonu bul" telefonu alarm sesiyle en yüksek seste çaldırır ve titretir; bildirimdeki "Buldum", bileklikten durdurmak ya da 1 dakika bitince durur. Bilekliği bul: cihaz kartındaki tuş bilekliği titretir (bağlantı açıkken).
- Hazır yanıtlar: bileklikte aramayı reddederken seçilen yanıt arayana SMS olarak gider (7/13, onay 7/14). Liste bağlanınca bileklikten okunur (7/9), Ayarlar'dan düzenlenip gönderilir (7/12). Arayanın numarası telefon durumu yayınından alınır (SMS, telefon durumu ve arama kaydı izinleri).
- Bileklikte hava durumu (varsayılan kapalı): Open-Meteo'dan (anahtarsız) saatte bir anlık durum, 7 günlük ve 24 saatlik tahmin çekilip bilekliğe gönderilir (komut 10/0, 10/1, 10/2; yer 10/7). Yer şehir aramasıyla ya da uygulama açıkken telefonun son konumuyla seçilir. Bileklik isteyince (10/3) son veri yeniden gönderilir.
- Bileklik alarmları (Ayarlar → Bileklik bildirimleri → Bileklik alarmları): alarmlar bileklikte kalır; FitWren listeyi okur (17/0), açıp kapatır, ekler (17/1), değiştirir (17/2), siler (17/4). Bir kez, her gün ya da seçilen günler.
- Takvim (varsayılan kapalı): önümüzdeki 7 günün etkinlikleri (en fazla 50, ilk hatırlatıcısıyla) her eşitlemede, değiştiyse bilekliğe gönderilir (12/1). Kapatınca bileklikteki takvim de kapatılır.
- Bileklik dili: tek dokunuşla bilekliğin menü dili FitWren'in diline ayarlanır (2/6, örneğin `tr_tr`). Saat biçimi (12/24) zaten her bağlantıda telefondan gidiyor.
- Bağlantı koptu uyarısı (varsayılan kapalı): bileklik bağlıyken bağlantı kopar ve 1 dakika içinde geri gelmezse telefonda uyarı çıkar; geri gelince uyarı kalkar. Bluetooth kapalıysa uyarı verilmez.
- Hava durumu: bileklikte Mi Fitness'tan kalan başka yerler varsa silinir (10/8) ve yer listesi FitWren'in yeriyle kurulur (10/6); yoksa bileklik "Bluetooth'u aç" diyordu. Mi Band 8 saatlik tahmini desteklemiyor (10/2 durum 1).
- Ayarlar sayfalara bölündü: ana sayfada konular (Adım kaynağı, Bileklik, Hedefler, Vücut, Su, Bildirimler, Görünüm, Veri, Hakkında) kısa özetleriyle listelenir, her biri kendi sayfasında açılır. Bileklik sayfası bildirimler, bileklik özellikleri ve uyarılar olarak gruplandı.
- Saat kadranları (Ayarlar → Bileklik → Saat kadranları): bileklikteki kadranlar listelenir (4/0), seçilir (4/1), kendi yüklenenler silinir (4/2). ".bin" dosyasından yeni kadran yüklenir: 4/4 ile kurulum istenir, 22/0 ile yükleme açılır, dosya `[0, 16, md5, boyut, dosya, crc32]` olarak 2048 baytlık parçalarla BLE yükleme kanalından (fe95 için 0055, sayaçsız şifreli) ya da SPP veri kanalından gönderilir, bitince kadran seçilir.
- Bildirimlerin gece durması: açık bağlantıda eşitleme sırasında bileklik 2 dakika hiç cevap vermezse bağlantı ölü sayılır ve yeniden kurulur. Bağlantı döngüsü beklenmeyen bir hatada durmaz. Bildirim dinleyicisi sistemce ayrılırsa yeniden bağlanmayı ister; bağlantı durmuşsa ilk gelen bildirim onu yeniden başlatır.

## 0.11.0

- Bileklik bildirimleri (Ayarlar → Bileklik bildirimleri, şimdilik Xiaomi ailesi): uygulama bildirimleri, gelen aramalar ve müzik kontrolü bilekliğe gider. Sesli gelen bildirimler gönderilir; bileklikte silinen bildirim telefondan da silinir. Arayan bileklikte görünür, bileklikten reddedilebilir (arama izniyle). Çalan şarkı bileklikte görünür; oynat, durdur, geç ve ses bileklikten yönetilir.
- Bunun için bağlantı açık kalır (`BandLive`). Kalıcı bildirim yok: FitWren bildirim erişimi iznini alır (`BandNotificationListener`) ve Android uygulamayı bu sayede açık tutar. Bileklik uzaklaşınca Android onu arka planda bekler (autoConnect), geri gelince yeniden bağlanılır.
- Bağlantı açıkken adımlar ve sağlık verileri aynı bağlantıdan 15 dakikada bir eşitlenir. "Eşitle" ve arka plan okumaları da bu bağlantıyı kullanır.

## 0.10.0

- Saate doğrudan bağlantı (Ayarlar → Saate doğrudan bağlan): FitWren, Xiaomi Smart Band 8 bilekliğe Bluetooth ile kendisi bağlanır ve dakikalık adımları okur. Mi Fitness ve Health Connect gerekmez. Bileklik saatleri ayrı tabloda (`band_hours`) saklanır ve telefonla saat saat birleştirilir. Uygulama açılınca ve 15 dakikada bir eşitlenir. Anahtar uygulama içinden Xiaomi hesabıyla giriş yapılarak alınır (QR kodlu giriş, şifre FitWren'e girilmez); elle girmek de mümkün. Bileklik aramada bulunmazsa adres elle yazılabilir. Bilekliğin takılı olduğu saatler (adım ya da nabız kaydı olan) adımsız olsa bile bileklikten sayılır; telefonun o saatlerde saydığı adımlar eklenmez. Şimdilik yalnızca adımlar okunur.
- Saate doğrudan bağlıyken nabız (dakikalık), SpO₂, stres ve uyku (süre ve evreler) de bileklikten okunur, `band_samples` ve `band_sleep` tablolarında saklanır; Sağlık sekmesi, geçmiş günler, Sağlık ve Özet widget'ları bu verileri gösterir. Bileklikte verisi olmayan eski günler için Health Connect'te kayıtlı veriler (örneğin Mi Fitness'ın yazdıkları) kullanılır.
- Health Connect seçeneği ayrı olarak duruyor (Samsung ve diğer saatler için). İkisi aynı anda açık olmaz.
- Health Connect'ten her okumada son 7 gün yeniden okunuyor; Mi Fitness'ın geç yazdığı adımlar kaçmıyor. Ayarlar'a "Bileklik verilerini yeniden eşitle" eklendi.
- Health Connect uykusu: üst üste binen ya da iki kez yazılmış uyku kayıtları artık bir kez sayılıyor ve uyku süresine uyanık geçen dakikalar katılmıyor. Bugün kartı, geçmiş günler ve Sağlık özeti aynı hesabı kullanıyor.
- Sağlık sekmesi yeniden düzenlendi: uyku, dinlenme nabzı, SpO₂, stres, egzersiz ve aktivite ayrı kartlarda. Her kartta en üstte son gün (uykuda son gece, evreleriyle), altında 7/30/90 günlük günlük grafik; bir çubuğa dokununca o gün gösterilir. Stres yalnızca doğrudan bağlantıda görünür. `BandPeriod` günlük SpO₂ ve stres taşıyor.
- Ayarlar kartlı gruplara ayrıldı. "Adım kaynağı" iki seçim oldu: telefon (sensör ya da HyperOS kaydı) ve bileklik (kullanma, doğrudan bağlantı, Health Connect). Bileklik bağlıyken en üstte resmi, adı, son eşitleme ve "Yeniden bağlan" tuşu var. Tema, widget ve dil "Görünüm ve dil" altında.
- Bugün sayfasında Paylaş'ın yanına "Eşitle" tuşu: doğrudan bağlantıda bilekliği hemen okur, Health Connect'te son 7 günü yeniden okur.
- SpO₂ artık iki yerden daha okunuyor: elle yapılan ölçümler (bileklikte "manuel ölçüm" dosyası, nabız ve stres de içinde) ve uyku sırasında alınan SpO₂ ve nabız örnekleri. Aynı dakikaya gelen değerler birleştiriliyor, biri diğerini silmiyor.
- Doğrudan bağlantı Xiaomi ailesine genişledi (deneysel, yalnızca Band 8 denendi): şifresiz BLE kullanan eski modeller (Mi Watch Lite, Redmi Watch, Mi Watch Color Sport, Redmi Band, 4C) ve klasik Bluetooth (SPP v1/v2) kullanan yeniler (Band 8 Pro, Band 9/9 Pro, Redmi Watch 4/5, Watch S1 Pro/S3). Model Bluetooth adından tanınır; ikisini de destekleyenlerde önce BLE denenir. Protokol kodu (`XiaomiSession`) bağlantı türünden ayrıldı.
- Huami/Amazfit ailesi de doğrudan bağlanıyor (deneysel): Mi Band 2–6, Amazfit Band/Bip/GTS/GTR/T-Rex/Neo, Zepp E. AES ile kimlik doğrulama, ardından dakikalık adım ve nabız okunur. Veri bileklikte bırakılır; her eşitleme son okunan saatin başından devam eder. Anahtar kurulum ekranında Zepp Life / Amazfit hesabıyla (e-posta ve şifre) giriş yapılarak uygulama içinden alınır; şifre yalnızca Zepp sunucusuna gider, kaydedilmez. Xiaomi hesabıyla açılmış Zepp Life hesaplarında çalışmaz; orada anahtar elle girilir.
- Ayarlar'daki cihaz kartı ve kurulumdaki arama listesi bağlı modele göre ad ve resim gösteriyor (bileklik, kare saat, yuvarlak saat); denenmemiş modellerde "deneysel" yazıyor. Health Connect'te son 7 günde veri yazan saat/bilekliğin adı gösteriliyor.
- Lisans MIT'den AGPL-3.0'a geçti: bileklik bağlantısı Gadgetbridge'in Xiaomi koduna dayanıyor.
- Veritabanı sürümü 6 (yeni `band_hours`, `band_samples`, `band_sleep` tabloları).

## 0.9.6

- Renk paleti tamamlandı: dinamik renk kapalıyken veya Android 12 öncesinde mor tonlar kalmadı, koyu temada dolgulu buton yazıları okunuyor; widget'lar da aynı paleti kullanıyor
- Tüm yazı stillerinde sabit genişlikli rakamlar: sayılar sayarken kaymıyor
- Açılış ekranı arka planı açık/koyu temaya uyuyor (açık temada koyu flaş yok)
- Hedef halkası ve adım sayısı animasyonla doluyor; sekmeler arasında yumuşak geçiş
- Erişilebilirlik: ekran okuyucu halkayı adım, hedef ve yüzdeyle okuyor; su "−" düğmesinin açıklaması var; son 7 gün grafiğinde hedefe ulaşan günlerde ✓
- Arka plan servisi bildirimi yalnızca adım sayısı değişince yeniliyor

## 0.9.5

- GitHub'dan güncelleme indirmeden önce, mağazanın denetimini atladığını söyleyen bir onay sorulur
- F-Droid ve IzzyOnDroid'e hazırlık: mağaza açıklamaları ve ekran görüntüleri (`fastlane/`), APK'da Google'ın bağımlılık bloğu kapatıldı
- README'ye Obtainium rozeti

## 0.9.4

- Su widget'ındaki +250 ml düğmesi artık suyu ekliyor

## 0.9.3

- Uygulamanın yeni adı FitWren: Adım Sayar & Sağlık
- Verilerin, ayarların ve widget'ların aynen duruyor
- Yeni ikon: hedef halkasında yürüyen sarı çalıkuşu

## 0.9.2

- Kuş widget'ında kalan adım artık kısa yazılıyor ve kesilmiyor
- Küçük Bugün widget'ında hedefe ne kadar kaldığını gösteren çubuk
- Yeni widget'lar: su (tek dokunuşla bardak ekleme), sanal rota, haftalık görevler, kilo ve günün özeti
- Widget'larda hedefe ulaşınca renk değişir ve ✓ çıkar; son güncelleme saati görünür
- Widget'a dokununca ilgili sayfa açılır; Ayarlar'dan widget arka planının saydamlığı seçilebilir
- Kuşa dokununca zıplıyor ve kanat çırpıyor, ara sıra göz kırpıyor

## 0.9.1

- Kuş widget'ı: seviye, sonraki seviyeye kalan adım ve seviye atlama
- Uyku ve nabız widget'ında yazılar artık kesilmiyor
- Isı haritasında boş günler ve az adımlı günler daha kolay ayırt ediliyor
- Kuş kartında seviye ve seviye atlama gösteriliyor

## 0.9.0

- Bugün ekranında su takibi: bardak ekle ya da çıkar, kilona göre önerilen günlük hedef. Sağlık sayfasında son 7 günün su grafiği
- İsteğe bağlı su hatırlatması (Ayarlar): 1, 2 ya da 3 saatte bir ne kadar su kaldığını söyler; bildirimden bardak eklenebilir
- Seri jokeri: her ay 2 hak. Hedefi kaçırdığın bir güne joker kullanırsan serin bozulmaz (Bugün ekranı ve Geçmiş)
- Madalyalar artık kalıcı: ilk kazandığın tarih saklanır, hedef değişince kaybolmaz
- Madalyalar sayfasında haftalık görevler: her hafta geçmişine göre seçilen 3 görev
- Bugün ekranında Paylaş düğmesi: günün özeti resim olarak paylaşılır
- İçe aktarma Samsung Health, Mi Fitness, Mi Fit ve Google Fit dışa aktarımlarını (CSV veya zip) da okur
- Üç yeni widget: bugün saat saat, son haftaların ısı haritası, uyku ve nabız
- Telefonun adımları artık saat saat de kaydedilir; HyperOS kaydı kapalıyken de Geçmiş’te saatlik grafik görünür
- Sağlık sayfasında ayrıntılı nabız: gün seçimi, dinlenme/en düşük/ortalama/en yüksek, eksenli grafik, saate dokununca ayrıntı, yaşa göre nabız bölgeleri ve açıklamalar. Ayarlar’a yaş eklendi
- Sanal rota: adımlarınla İstanbul'dan Ankara'ya ya da Camino'da yürü
- Kuş: toplam adımınla büyüyen, ruh hali bugüne göre değişen avatar ve widget'ı

## 0.8.0

Eklenenler
- Sağlık sayfasında uyku evreleri (derin, hafif, REM, uyanık), ortalama yatış ve kalkış saati, kan oksijeni (SpO2), egzersiz türleri ve egzersiz yapılan günler, aktif kalori ve dinlenme nabzının önceki döneme göre değişimi.
- Bugünün saatlik nabız grafiği.
- Kilo kaydı: kilonu Sağlık sayfasından ekle; vücut kitle endeksi, toplam değişim, son ölçümden beri değişim ve grafik. Ayarlar'da isteğe bağlı "Haftalık kilo hatırlatması" (haftanın ilk günü 09:00 civarı).
- Bileklik açıkken haftalık özet bildirimi ortalama dinlenme nabzını ve uykuyu da gösterir.
- Sağlık sayfasında bileklik ya da saat bağlı değilse uyarı ve "Bileklik ve saat kurulumu" ekranı: Health Connect kurulumu, markaya göre (Xiaomi, Samsung, Google/Fitbit, Garmin, Huawei, diğer) ayar yolu, izinler ve son 7 günde Health Connect'e veri yazan uygulamaların listesi; uygulama bildiriyorsa cihazın türü (bileklik, saat) ve modeli de görünür.
- Yeni alt menü: Geçmiş, Sağlık, Bugün (ortada, büyük, açılışta seçili), Madalyalar, Ayarlar. Ayarlar sağ üstteki dişliden alta taşındı.
- Sağlık sayfasında bileklik için "Sağlık özeti": son 7 ya da 30 günün ortalama ve en uzun uykusu, ortalama dinlenme nabzı ve grafiği, toplam ve en uzun egzersiz, bileklik mesafesi. Veriler saklanmaz, her açılışta Health Connect'ten okunur.
- Güncelleme uygulamanın içinden: Ayarlar → "Güncellemeleri kontrol et" yeni sürümün APK'sını GitHub'dan indirir ve Android'in kurulum ekranını açar. İlk seferde Android bu uygulamadan yüklemeye izin ister.

Düzeltilenler
- Uygulama açıkken bileklik adımları dakikada bir okunur (önce 5 dakikada bir). Mi Fitness veriyi gecikmeli yazdığında yeni sayı daha çabuk görünür.

## 0.7.0

Eklenenler
- Bileklik adımları (Ayarlar → "Bileklik adımlarını kullan", varsayılan kapalı): Mi Band gibi bilekliklerin Mi Fitness'ın Health Connect'e yazdığı adımları okunur. Bilekliğin adım yazdığı saatlerde bileklik, diğer saatlerde telefon sayılır. Health Connect'e hiçbir şey yazılmaz.
- Geçmiş'te seçilen gün için bileklik verileri: egzersizler, mesafe, aktif kalori, nabız (7 günlük dinlenme nabzı grafiği) ve uyku. Bu veriler saklanmaz, her seferinde Health Connect'ten okunur.

Düzeltilenler
- Samsung'da uygulama kapalıyken 15 dakikalık okumalar sensöre ulaşamıyordu; artık kısa süreli bir servisle okunuyor, kalıcı bildirim yok.

Not: Veritabanı sürümü yükseldi. 0.7.0'dan sonra 0.6.1'e geri dönülemez (önce CSV dışa aktarın).

## 0.6.1

Eklenenler
- Ayarlar'da "Güncellemeleri kontrol et": GitHub'daki son sürümle karşılaştırır, yeni sürüm varsa indirme sayfasını açar. Yalnızca dokununca çalışır, arka planda kontrol yapılmaz.
- Ayarlar'da GitHub sayfası bağlantısı (kaynak kod, sürümler, hata bildirimi).
- Henüz yayınlanmış sürüm yoksa güncelleme kontrolü bunu ayrıca söyler.

Depo ve derleme
- İmza anahtarı depodan çıkarıldı; APK artık GitHub Secret'larında saklanan yeni bir anahtarla imzalanıyor. Bu sürüme geçerken uygulamayı bir kez kaldırıp kurmak gerekir (önce CSV dışa aktar).
- `v*` etiketi gönderilince APK otomatik derlenip GitHub Releases'te yayınlanıyor.
- Açık kaynak README, ekran görüntüleri ve MIT lisansı.

## 0.6.0

Eklenenler
- Haftalık özet bildirimi: haftanın son günü akşamı haftanın toplamı ve geçen haftayla fark.
- Hedefe yaklaşınca bildirim (günlük hedefin %80'i).
- Geçmiş'te yıllık özet: toplam, günlük ortalama, hedef günleri, en iyi ay ve gün, kazanılan madalyalar.

## 0.5.0

Eklenenler
- Madalyalarda kaç kez kazanıldığı (ör. `5x`).
- Widget'lar telefonun renklerini (Material You) veya uygulamanın temasını kullanıyor.
- Widget'lar boyutlarına göre düzenleniyor: küçültülünce sadeleşir, büyütülünce ayrıntı ekler.
- Yeni widget'lar: Seri ve hafta, Aylık takvim.
- 10 yeni dil: Almanca, İspanyolca, Fransızca, İtalyanca, Portekizce, Rusça, Arapça, Japonca, Çince, Korece.

Düzeltilenler
- Arapçada sağdan sola düzen, Latin rakamlar, her dilde yerel tarih sırası ve birimler.

## 0.4.0

Eklenenler
- Bugün widget'ında hedef halkası.
- Erken kalkan madalyası (08:00'den önce 1.000 adım; HyperOS kaydı açıkken).
- İngilizce dil seçeneği.

Düzeltilenler
- Üst çubuk durum çubuğunun altına alındı; Geçmiş'te ay değişince seçili gün o aya taşınıyor.
- 15 dakikalık arka plan okuması, widget'ların kullandığı WorkManager ile aynı iş numarasını aldığı için hiç çalışmıyordu; ayrı numaraya taşındı.

## 0.3.0

Eklenenler
- Boy ve kilodan mesafe ve kalori tahmini (km veya mil).
- Geçmiş'te seçilen günün saat saat dağılımı ve yürüyüş/koşu ayrımı (HyperOS kaydı açıkken).
- En iyi ay rekoru, hafta başlangıcı seçimi (pazartesi veya pazar).
- İsteğe bağlı bildirimler: hedefe ulaşınca, akşam seri hatırlatması.
- CSV ile dışa ve içe aktarma.

Düzeltilenler
- HyperOS uyarı kartı çıkınca kaynak anahtarı da uyarının altında gösteriliyor.
- Seri hesabında bugünden önceki eksik gün doğru sayılıyor.

## 0.2.0

Eklenenler
- Yeni tasarım: alt menüde Bugün, Geçmiş ve Madalyalar.
- Bugün ekranında hedef halkası, seri ve haftalık ilerleme.
- Geçmiş: aylık takvim, gün ayrıntısı ve kişisel rekorlar.
- Madalyalar ve seviye: günlük adım, seri, haftalık hedef, toplam yol.
- Ayarlanabilir günlük ve haftalık hedef; widget da yeni hedefi gösteriyor.
- Ayarlar: açık, koyu veya sistem teması, telefonun renkleri, adım kaynağı anahtarları ve okuma kaydı.

## 0.1.0

Eklenenler
- İlk sürüm: adım sensöründen okuma, günlük kayıt, Bugün ve 7 günlük tablo widget'ları.
- "Arka planda sürekli say" seçeneği ve marka bazlı pil yardım kartı.
- Xiaomi'de HyperOS adım kaydından okuma; geçmiş günler de içe alınıyor.
- HyperOS kaydı okunamazsa veya ilerlemezse uyarı kartı.

Düzeltilenler
- Arka plan servisinin 60 saniyede bir toplu gelen eski sayaç değerleri, yeni değerlerden sonra gelince adımlar şişiyordu (50 adım 424 görünüyordu). Okumalar artık olay zaman damgasıyla sıralanıyor, telefonun yeniden başlatılması açılış sayacından anlaşılıyor.
- CI derlemeleri sabit bir anahtarla imzalandı ki yeni sürüm eskisinin üstüne kurulabilsin.

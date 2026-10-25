# Sürüm geçmişi

Her sürümde nelerin eklendiği ve nelerin düzeltildiği, yeniden eskiye. Uygulamada da Ayarlar → Sürüm notları altında, seçilen dilde görülebilir. APK'lar: [Releases](https://github.com/DorDeorz/adimsayar/releases).

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

# Sürüm geçmişi

Her sürümde nelerin eklendiği ve nelerin düzeltildiği, yeniden eskiye. Uygulamada da Ayarlar → Sürüm notları altında, seçilen dilde görülebilir. APK'lar: [Releases](https://github.com/DorDeorz/adimsayar/releases).

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

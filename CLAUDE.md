# CLAUDE.md

Bu dosya Claude Code için proje talimatlarıdır. Kod yazmaya başlamadan önce oku.

## Proje

AdımSayar — telefon için adım sayma uygulaması. İki platformda (iOS ve Android) çalışacak.

## Durum

Repo boş bir iskelettir. Şunlar henüz kararlaştırılmamıştır ve bunları **kullanıcıya sor, kendin karar verme**:

- Framework / dil seçimi
- Adım sayma API'si (aşağıdaki seçeneklere bak)
- Veri saklama yöntemi (sunucu gerekli mi, sadece cihazda mı)
- Kimlik doğrulama gerekip gerekmediği

## Adım sayma için notlar

Platformların hazır pedometre desteği var, GPS'e gerek yok:

- **iOS**: Core Motion (`CMPedometer`). `NSMotionUsageDescription` izin anahtarı gereklidir.
- **Android**: İki seçenek — `Sensor.TYPE_STEP_COUNTER` (donanım sayaacı, düşük pil) veya Google **Health Connect** (`androidx.health.connect`). Android 10+ için Health Connect daha standarttır.
- **Expo** kullanılırsa: `expo-sensors` (`Pedometer`) modülü mevcuttur. Expo Go'da çalışır ama bazı özellikler native build gerektirir.

Bunların hangisinin kullanılacağına sen karar verme, önce kullanıcıya sor.

## Kurallar

- Kullanıcının seçtiği stack dışına çıkma; yeni bağımlılık eklemeden önce mevcut çözümü kullan.
- Erişilebilirlik ve pil verimliliği öncelikli: bir pedometre arka planda sürekli çalışır, yanlış tasarım pil yiyebilir.
- Kullanıcı verisini cihaz dışına gönderme (hesap/ağ eklenene kadar).
- Yorum satırı yazma; kod kendini açıklasın.
- Türkçe arayüz metinleri kullanıcıya sorulmadan değiştirme.
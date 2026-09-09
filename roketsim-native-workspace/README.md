# RoketSim Native

RoketSim Native, model roketler için Python ile geliştirilmiş bir uçuş simülasyonu
motorudur. Güncel sürüm sabit adımlı, doğrulanmış bir 3DOF balistik yetenek sunar;
OpenRocket ileride parite ve referans hedefidir, uygulama otoritesi değildir.

## Yetenekler

- Standart atmosfer, yerçekimi, sabit rüzgâr ve bağıl hava akışı
- Araç geometrisi, AeroTech F50-4T itki modeli ve değişken motor/roket kütlesi
- Basic Drag V1 ve 3DOF öteleme dinamiği
- Klasik sabit adımlı RK4 integrasyonu
- BURNOUT, APOGEE ve GROUND olayları
- Yörünge kaydı ve değiştirilemez simülasyon sonucu

## Mimari

```text
Çevre
  ↓
Araç / İtki
  ↓
Aerodinamik
  ↓
Dinamik
  ↓
RK4
  ↓
Olaylar / Kaydedici
  ↓
Simülasyon Motoru
  ↓
Simülasyon Sonucu
```

## Çalıştırma

Proje Python 3.12 veya üzerini ve `numpy` paketini gerektirir. Depo kökünden:

```powershell
cd roketsim-native-engine
python -m pip install --group dev -e .
python -m pytest
```

JSON CLI, kurulu ortamdan tek bir isteği stdin üzerinden alır:

```powershell
Get-Content ..\examples\integration\request-v1.json -Raw |
  python -m roketsim_native.integration.cli
```

## Java 17 Entegrasyonu

Java frontend, yerel JSON CLI sürecini `ProcessBuilder` ile başlatır; istek stdin'e,
tek JSON yanıt stdout'a yazılır. Şema 1.1, `capabilities` sorgusunu ve SI birimli açık
araç yapılandırmasıyla `simulate` işlemini destekler; şema 1.0 preset istekleriyle
uyumluluk korunur. Sözleşmeler `schemas/integration/`, standart kütüphane örneği
ise `examples/integration/Java17CliBridgeExample.java` altındadır. Bu sınır bir ağ
veya sunucu API'si değildir.

## Dokümantasyon

- [environment.md](docs/environment.md)
- [vehicle-propulsion.md](docs/vehicle-propulsion.md)
- [aerodynamics.md](docs/aerodynamics.md)
- [dynamics-numerics.md](docs/dynamics-numerics.md)
- [simulation-engine.md](docs/simulation-engine.md)
- [demo-validation.md](docs/demo-validation.md)

Ayrıntılı tarihsel doğrulama kayıtları `docs/archive/nat/` altında korunur.

## Güncel Sınırlamalar

- Simülasyon ignition anında başlar; Liftoff ve launch rail fiziği yoktur.
- Recovery modellenmez.
- Model 3DOF'tur; attitude ve momentler yoktur.
- İntegrasyon sabit adımlıdır.
- Aerodinamik kapsam Basic Drag V1 ile sınırlıdır.
- Henüz OpenRocket paritesi iddiası yoktur.

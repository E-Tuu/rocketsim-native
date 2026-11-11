# Çevre

## Güncel V1 Kapsamı

- WORLD, yerel ENU çerçevesini kullanır: +x Doğu, +y Kuzey ve +z Yukarı.
- Kabul edilmiş irtifa işleme zinciri, yerel WORLD düşey yer değiştirmesini atmosfer
  modelinin sahip olduğu geopotential-height girdisinden ayırır.
- Alt U.S. Standard Atmosphere modeli, doğrulanmış alanında kuru hava basıncı,
  sıcaklığı ve yoğunluğu sağlar.
- Kuru hava özellikleri, kabul edilmiş atmosfer durumundan ses hızı, dinamik
  viskozite, kinematik viskozite ve özgül ısı oranını üretir.
- Yerçekimi, kabul edilmiş sabit standard-g V1 modelinin sağladığı upstream WORLD
  ivmesidir.
- Rüzgâr modelleri sabit hava-kütlesi hızını sağlar; doğrulanmış demo sıfır rüzgâr
  kullanır.
- Bağıl akışın authority'si `V_rel = V_rocket - V_airmass` convention'ıdır.
- Temel uçuş koşulları hava hızına, Mach'a, Reynolds sayısına ve dinamik basınca
  sahiptir; aerodinamik bu sonuçları yeniden hesaplamadan tüketir.
- Güncel doğrulanmış zincir, ilk Native 3DOF demoda kullanılan irtifa ve hız
  aralığını destekler.

## Kapsam Dışı / Planlanan

- Türbülans, ani rüzgâr değişimleri ve stokastik rüzgâr ertelenmiştir.
- Zamanla değişen ve gelişmiş irtifa-bağımlı rüzgâr profilleri ertelenmiştir.
- Konuma bağlı WGS84 ve gelişmiş yerçekimi modelleri ertelenmiştir.
- Dünya dönüşü ve Coriolis ivmesi ertelenmiştir.
- Arazi, coğrafi koordinatlar ve daha geniş çevre uzantıları ertelenmiştir.

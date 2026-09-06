# ADR-002 SI Units

## Karar

Physics core yalnız SI birimleri kullanacaktır. Radian, core içindeki canonical açı birimidir.

## Gerekçe

Tek bir iç birim sistemi dönüşüm ve ölçek hatalarını azaltır; teknik spesifikasyonun CONV-001 kararını uygular.

## Sonuçlar

UI ve file adapter'ları presentation units ile SI arasındaki dönüşümden sorumludur. Presentation units physics core'a taşınmayacak ve aynı scope içinde farklı birim sistemleri karıştırılmayacaktır. Internal eksik veya uygulanamaz floating data için NaN politikası korunacak; gelecekte JSON/API sınırında null'a dönüşüm yapılacaktır.

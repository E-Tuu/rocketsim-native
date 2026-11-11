# Doğrulama

Doğrulama, analitik hesap kontrollerini, uygulamalar arası uyumu ve bağımsız
sayısal karşılaştırmayı ayrı değerlendirir. Sentetik senaryolar, kullanılan
modelin ve belirli girdilerin davranışını sınar.

## Özgün mühendislik adayının kanıtı

- Özgün mühendislik adayında **792 Java testi** ve paketlenmiş kütüphaneyi
  kullanan **altı Java örneği** geçti.
- F50 senaryosunun iki zaman adımı için Java–Python uyumu
  **442.261 farklı karşılaştırma** ile sınandı. Sonuçların 442.225'i tam
  eşleşti, 36'sı önceden belirlenmiş toleransların içinde kaldı.
- Bağımsız L doğrudan uçuş hesabında 42 analitik kontrol ve üç sayısal
  inceltme düzeyi kullanıldı. Yerel sonuçlarla karşılaştırmada **10 kesin
  ölçütlü karşılaştırma geçti**, **20 yörünge ve olay karşılaştırması
  yalnızca raporlandı**. Bunlara bir geçme eşiği uygulanmadı. Yer temasında
  interpolasyon katkısı tek başına ayrıştırılamadı.
- Sentetik L/M/N konfigürasyonları doğrudan uçuşu, kılavuzu, kurtarmayı
  ve seçili girdi değişikliklerine duyarlılığı kapsar.

Bu sonuçlar ölçülmüş gerçek uçuş performansını veya OpenRocket
eşdeğerliğini doğrulamaz. Bağımsız L karşılaştırmasındaki sayısal
belirsizlik deneysel olarak tahmin edilmiştir.

## Tarihsel kanıtlara erişim

- [Özgün model kayıtları](archive/nat/), [tasarım kararları](decisions/)
  ve [önceki özet belgeler](archive/guides/)
- [Doğrulama kayıtları](verification/) ve
  [ENG kaynak kayıtları](verification/motor-001-teknofest-eng-import.md)
- [Java–Python karşılaştırma ölçütleri](verification/j-011-native-trajectory-vv.md)
- [Uçuş olayları](verification/nat-037-n37-04b1-integrated-flight-semantics.md)
  ve [duyarlılık](verification/nat-038-model-applicability-sensitivity.md)
- [Bağımsız L karşılaştırması](verification/nat-039-independent-l-flight.md)
  ve [çekirdek kabul kapsamı](verification/nat-040-3dof-acceptance.md)
- [Public yayın adayı manifestosu](verification/publication-3dof-core-clean.json)

## Public adayın doğrulama kapsamı

Bu yayın ağacı üç özgün TEKNOFEST Rev1 ENG dosyasını içerir. İlgili katalog
testleri, L/M/N uçuş testleri, NAT-039 L referansı ve iki paket örneği bu
verileri gerektirir. Verisiz adayda 85 test bu kaynakların yokluğu nedeniyle
başarısız oldu. Dosyaların SHA-256 değerleri doğrulandı. Son yayın ağacında
testler yeniden çalıştırılmadı. Yukarıdaki sonuçlar özgün mühendislik
adayına aittir. Motor verilerinin yeniden dağıtım izni doğrulanmadı.

Bu depodaki tarihsel raporların yayın kopyalarında özel makine yolları
çıkarılmıştır. Depoda yer almayan ham kanıtlar belirtilir. Özgün raporlar
ve manifestolar geliştirme ortamında korunur.

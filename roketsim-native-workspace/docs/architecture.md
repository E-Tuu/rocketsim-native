# Model sınırları ve mimari

Bu ikincil belge, fizik modellerinin nerede kullanıldığını ve hangi
sonuçları ürettiğini açıklar.

## Fizik çekirdeği

Java 17 çekirdeği, geometri ve kütle özelliklerini, motoru, çevreyi ve
uçuş koşullarını açık girdiler olarak alır. Araç hareketi için
[geometri ve kütle](geometry-mass.md), [motor](propulsion.md) ve
[çevre ve aerodinamik](aerodynamics-environment.md) sonuçları kullanılır.
[Uçuş modeli](flight-simulation.md) kabul edilmiş durumları, olay
tahminlerini ve sonlanma nedenini ayrı bildirir.

Temel büyüklükler SI birimlerindedir. Dünya konumu doğu, kuzey ve yukarı
yönlerine göre ifade edilir. Bileşen yerleşimi ise burun ucundan başlayan,
kuyruğa doğru artan araç eksenindeki bir mesafedir. Atalet için gövdenin
boyuna pozitif ekseni ileri yönlüdür. Bu koordinatlar farklı amaçlara
hizmet eder.

## Başvuru motoru ve eski entegrasyon

[Python motoru](../roketsim-native-engine/) doğrulama başvurusu olarak
korunur. Önceki yerel JSON arayüzü standart girdiden bir istek alır ve
standart çıktıya bir yanıt verir. [Şemalar](../schemas/integration/) ve
[örnekler](../examples/integration/) bu arayüzü belgeler. Güncel Java
çekirdeği doğrudan Java nesneleriyle kullanılabilir.

[Doğrulama özeti](verification.md) ve [tarihsel tasarım kararları](decisions/)
modelin sınırlarını ve bunların dayanaklarını gösterir.

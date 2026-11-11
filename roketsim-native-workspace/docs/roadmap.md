# Yol haritası

Bugünkü çekirdek, tek kademeli ve tek motorlu araçlar için sabit yönlü
öteleme uçuşunu kapsar. Aşağıdaki başlıklar geliştirme yönlerini gösterir.

## Uygulama entegrasyonu

Kullanıcı arayüzü model girdilerini, birimleri ve geçerlilik sınırlarını
görünür kılmalıdır. Uçuş gösteriminde kabul edilmiş durumlar ve tahmini
olaylar ayrı sunulmalıdır. Motor kaynakları, ölçüm bilgileri ve model
seçimleri sonuçla birlikte saklanabilmelidir.

## Genişletilecek fizik

- **Araç bileşenleri.** Yeni bileşenler için geometri, kütle, atalet ve
  aerodinamik davranış birlikte tanımlanıp doğrulanacaktır.
- **Aerodinamik.** Daha geniş Mach ve hücum açısı kapsamı kendi
  geçerlilik ölçütleriyle ele alınacaktır.
- **Dönme dinamiği.** Açısal durum, momentler ve tutum entegrasyonu ayrı
  çalışma gerektirir. Bugünkü atalet hesapları bu hareketi çözmez.

## Ertelenen karşılaştırma

OpenRocket ile çalıştırılabilir karşılaştırma uygulama entegrasyonu
aşamasına ertelenmiştir. Karşılaştırma için yazılım sürümü, araç ve motor
kaynağı, başlangıç durumu, kuvvet varsayımları ve olay tanımları
eşleştirilmelidir. Sonuçlar model farklarıyla birlikte yorumlanacaktır.

[Güncel model kanıtları](verification.md) ·
[Doğrulanmış kapsam](verification/nat-040-3dof-acceptance.md)

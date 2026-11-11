# Geometri, kütle ve atalet

## Amaç

Roketin boyutlarını, bileşen hacimlerini, kütlesini ve eksenel kütle
merkezini belirler. Uygun model girdileriyle atalet özelliklerini de hesaplar.

## Kullanılan Fiziksel Model

Geometrik boyutlar metre cinsindedir. Eksenel konum $x$, burun ucundan
kuyruğa doğru artar. Azami çap referansı kullanıldığında
$A_\mathrm{ref}=\pi D_\mathrm{max}^2/4$ alanı m² cinsindedir.

Bir bileşenin hesaplanan kütlesi $m_i=\rho_i V_i$ bağıntısıyla bulunur.
Yoğunluk kg/m³, hacim m³ ve kütle kg cinsindedir. Yapı, o andaki motor
ve ayrı sayılan yardımcı yükler birlikte değerlendirilir.

$$
m=\sum_i m_i,\qquad x_\mathrm{KM}=\frac{\sum_i m_i x_i}{m}.
$$

Atalet tensörü kg·m² cinsindedir. Uygun bileşen tensörleri, paralel eksen
teoremiyle ortak kütle merkezine taşınır ve toplanır. Eksenleri paralel
bileşen için öteleme terimi şöyledir.

$$
\Delta I_i=m_i[(\mathbf d_i\cdot\mathbf d_i)\mathbf 1-
\mathbf d_i\mathbf d_i^\mathsf{T}] .
$$

$\mathbf d_i$ metre cinsinden öteleme vektörü, $\mathbf 1$ birim matristir.

## Mevcut Özellikler

- Dolu veya kabuk konik burun, silindirik gövde, eşit aralıklı tek bir
  trapez kanat takımı, eş eksenli motor yuvası ve iki merkezleme halkası.
- Malzeme yoğunluğundan hesaplanan bileşen kütleleri. Ölçülmüş kütle veya
  kütle merkezi, desteklenen bileşenlerde ilgili hesaplanan değerin yerine
  geçebilir. Bir ölçüme dâhil edilen yardımcı yük ayrıca sayılmaz.
- Uygun yapı ve motor modelleriyle bileşen ataletlerinin hesabı. Araç
  toplamı, desteklenen eş eksenli yerleşim ve gerekli bileşen verileriyle
  ayrı bir hesap olarak alınabilir.

## Varsayımlar ve Sınırlamalar

Hesaplanan kütleler geometriye ve verilen malzeme yoğunluğuna bağlıdır.
Ölçüme dayalı değerler, sağlanan ölçüm kaydının kapsamını taşır.
Genel eksen dışı yükler için üç boyutlu araç kütle merkezi ve atalet
birleştirme desteği yoktur. Atalet hesabı, öteleme çözümüne dönme momenti
veya tutum dinamiği eklemez. Uçuş ivmesinde güncel toplam kütle kullanılır.

## Planlanan Geliştirmeler

Daha geniş bileşen aileleri ve yerleşim seçenekleri için geometri, kütle ve
atalet birlikte doğrulanacaktır. Dönme dinamiği ayrı bir geliştirme alanıdır.

[Geometri ve kütle doğrulaması](verification/nat-037-n37-03c-parametric-vehicle-integration.md#independent-oracles-and-comparison-policy) ·
[Atalet doğrulaması](verification/nat-025c-vehicle-inertia-aggregation.md)

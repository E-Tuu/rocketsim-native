# Atmosfer ve aerodinamik

## Amaç

Roketin bulunduğu noktadaki hava durumunu ve havaya göre hareketini
belirler. Bu bilgilerle sürükleme kuvveti ve ayrı bir statik stabilite
göstergesi hesaplanır.

## Kullanılan Fiziksel Model

Alt ABD Standart Atmosferi, sıcaklık (K), basınç (Pa) ve yoğunluk (kg/m³)
verir. Jeopotansiyel irtifa girdisi, fırlatma irtifasına başlangıca göre
düşey yer değiştirme eklenerek bulunur. Kuru hava modeli ses hızını ve
viskoziteyi sağlar. Rüzgâr, havanın yere göre hızıdır.

$$
\mathbf v_\mathrm{bağıl}=\mathbf v-\mathbf v_\mathrm{hava},\qquad
q=\tfrac12\rho|\mathbf v_\mathrm{bağıl}|^2,\qquad
M=|\mathbf v_\mathrm{bağıl}|/a .
$$

Hızlar ve ses hızı $a$ m/s, dinamik basınç $q$ Pa cinsindedir.
Reynolds sayısı $Re=|\mathbf v_\mathrm{bağıl}|L/\nu$ bağıntısıyla
bulunur. $L$ karakteristik uzunluk (m), $\nu$ kinematik viskozitedir (m²/s).
Basic Drag modeli sürtünme, basınç ve taban sürüklemesini birleştirir.
Sürükleme büyüklüğü $q A_\mathrm{ref} C_{D0}$ (N), yönü bağıl hızın
tersidir. Bağıl hız sıfırken kuvvet sıfırdır.

Statik hesap, sıfıra yakın hücum açısı için doğrusallaştırılmış
Extended-Barrowman yaklaşımını kullanır. CP basınç merkezini, CNa normal
kuvvet katsayısının hücum açısına göre eğimini (rad⁻¹) gösterir.
Kuyruğa doğru artan eksende statik marj $(x_\mathrm{CP}-x_\mathrm{KM})/D_\mathrm{max}$
olarak, çap birimiyle ifade edilir.

## Mevcut Özellikler

- Kuru standart atmosfer, sıfır veya sabit üç bileşenli rüzgâr, bağıl hız,
  Mach, Reynolds sayısı ve dinamik basınç.
- Burun, gövde ve kanat takımı için ayrı yüzey pürüzlülüğü. Reynolds
  sayısı ve pürüzlülük sürtünme hesabını etkiler.
- Desteklenen geometri için statik CP, CNa ve kütle merkezi verildiğinde
  statik marj. Bu göstergeler uçuşa yön veya dönme momenti uygulamaz.

## Varsayımlar ve Sınırlamalar

Atmosfer aralığı **−5000 ile 84852 jeopotansiyel metre** arasındadır ve
sınırlar dâhildir. Basic Drag sıfır hücum açısında **0 ≤ Mach ≤ 1** için,
statik CP/CNa ise **0 ≤ Mach < 0,8** için geçerlidir. Zamanla değişen
rüzgâr, türbülans ve genel transonik ya da süpersonik kuvvet modeli
desteklenmez.

## Planlanan Geliştirmeler

Genişletilmiş akış koşulları, aerodinamik kuvvet ve moment modelleri
kendi geçerlilik aralıkları ve doğrulama verileriyle ele alınacaktır.

[Atmosfer doğrulaması](archive/nat/nat-009e-atmosphere-vv.md) ·
[Sürükleme modeli](archive/nat/nat-012a1-basic-drag.md) ·
[Statik stabilite modeli](archive/nat/nat-012b-static-stability.md)

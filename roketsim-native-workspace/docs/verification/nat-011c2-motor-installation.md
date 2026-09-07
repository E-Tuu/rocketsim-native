# NAT-011C.2 — Motor Installation

Başlangıç: `85f36f5` (NAT-011C.1), temiz çalışma ağacı; 2026-09-07
tam başlangıç testi 857 PASS. `88ab5bc` ve `4fcdf7b` ataları doğrulandı.

## Sahiplik ve sözleşme

`propulsion.installation` yalnız geçerli Geometry ve MotorDefinition
arasındaki nominal kurulum ilişkisini çözer. Geometry montaj ölçülerini,
yerleşimini ve motor aft referansını; MotorDefinition seçilen motor ölçülerini
ve verisini sahiplenir. Yeniden geometri hesabı/katalog araması yapılmaz.

Parametresiz `MotorInstallationResolver.resolve(*, resolved_geometry, motor)`
durum tutmaz. Frozen/slotted `MotorInstallation`, seçilen exact motor nesnesini
ve yalnız front/aft x_geo, nominal radial clearance, axial engagement döndürür.
Koordinat burun ucunda sıfırdır; +x_geo kuyruğa doğrudur.

- Aft = Geometry motor_aft_reference_x_geo_m.
- Front = aft − MotorDefinition.length_m.
- Çap uyumu: D_motor <= D_mount; radyal açıklık = (D_mount − D_motor)/2.
- Ön containment: front >= mount_start.
- Örtüşme = min(aft, mount_end) − max(front, mount_start), kesinlikle > 0.

Çap ve ön sınır eşitliği geçerlidir; çap eşitliği yalnız nominal geometrik
uyumdur. Üretim toleransı/sıkı geçme modellenmez. Pozitif, sıfır veya negatif
overhang gerçek örtüşme pozitifse geçerli olabilir. Sıfır/negatif örtüşme
onarılmaz. Yerleşim değiştirilmez; clamp/fallback yoktur.

`MotorInstallationError(ValueError)` error_code/field_name/value saklar.
Uyumsuzluk ve beklenmeyen non-finite türetimler bu hatayı üretir. min/max'ın
NaN sınırını gizlememesi için tüketilen montaj aralık sınırları da kontrol edilir.
Bu savunma Geometry denklemlerini veya MotorDefinition doğrulamasını değiştirmez.

## Test eşlemesi

- MINST-T01..03: sonuç değişmezliği ve public imza.
- T04..05: hazır aft ve front çıkarımı.
- T06..11: küçük/eşit/büyük çap, radyal açıklık.
- T12..14: ön yüz containment sınırları.
- T15..21: üç overhang işareti, gerçek interval kesişimi ve pozitif örtüşme.
- T22..24: sıfır/negatif örtüşme ve aşırı aft yerleşim reddi.
- T25..28: gerçek GeometryResolver + katalog F50 entegrasyonu.
- T29..32: exact motor provenance, girdilerin korunması, determinizm, structured hata.
- T33..34: bozuk upstream değerler ve sonlu operandlardan taşan türetimler.
- T35..36: değiştirilmiş resolved/motor fixture üzerinden otorite sınırları;
  raw Geometry veya katalog varsayımlarıyla yeniden hesap yapılmadığının kanıtı.
- T37..40: yalnız nominal tek/eş eksenli API; tolerans, cant, offset, cluster,
  itki, mass/CG veya event yüzeyi yoktur.

Analitik A.2/F50 fixture: front 0.907 m, aft 1.005 m, radial clearance 0 m,
engagement 0.093 m. Strict denklem toleransı kullanılır; bunlar varsayılan
motor/montaj ölçüleri değildir.

MotorDefinition/catalog ve önceki fizik paketleri değişmeden korunur.
İtki değerlendirmesi, motor kütlesi/CG, toplam roket kütlesi/CG C.3 ve sonraki
gate'lere ertelidir. Üretim toleransı, retention hardware ve çoklu/eğik motor
modelleri kapsam dışıdır. Yeni bağımlılık veya runtime ağ erişimi yoktur.

## Sonuç

Odaklı MINST testleri 34 PASS; tüm propulsion 131 PASS. Geometry 184,
mass 55, materials 13, flight_conditions 98, environment/V&V 260,
math/V&V 148 PASS. Full 891 PASS = 857 mevcut + 34 yeni test.
Dondurulmuş sözleşmeden sapma yoktur.

NAT-011C.2 IMPLEMENTATION GATE: PASS.

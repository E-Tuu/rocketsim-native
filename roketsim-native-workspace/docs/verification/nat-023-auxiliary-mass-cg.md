# NAT-023 — Auxiliary Mass ve Axial CG Accounting

Tarih: 2026-10-28. Kapsam: Java 17 Core'da açık yardımcı donanım kütlelerinin mevcut toplam mass/CG hesabına eklenmesi; yeni fizik veya configuration entegrasyonu değildir.

## Baseline ve değişiklik sınırı

- Başlangıç: kabul edilmiş V2-000 commit'i `f14195d00b044a298cec61b9ab77d1d05a0963e8`; clean worktree ve J-011 `480f64840b92de87ee70a30a299fdb6ab169e0ef` ancestry doğrulandı. V2-000 commit'indeki iki belge kabul durumundadır.
- Dal: `development/nat-023-auxiliary-mass-cg`, doğrudan V2-000 commit'inden oluşturuldu. Main/J-011 değiştirilmedi; push yapılmaz.
- Tek production değişikliği: `roketsim-core/src/main/java/org/roketsim/core/mass/RocketMassPropertiesCalculator.java` içindeki yeni overload ve sınıf açıklaması.
- Yeni odaklı test: `roketsim-core/src/test/java/org/roketsim/core/AuxiliaryMassTest.java`. Tarihsel `RocketMassTest` ve diğer kabul testleri değiştirilmedi; mevcut structural/installation fixture'ları tekrar kullanılır. Ayrı sınıf, yeni input sözleşmesi testlerini legacy testlerden ayırır; yeni test altyapısı değildir.
- Structural, material, geometry, propulsion, model profile, PhysicsEvaluator, engine, event, recorder ve result kaynaklarında değişiklik yok. Python ve tarihsel kayıtlar korunur. Mevcut native-engine register yalnız `docs/archive/nat/` içindedir; yeni V2 için aktif register bulunmadığından tarihsel kayıt güncellenmedi. V2-000 belgeleri değiştirilmedi.

## API, otorite ve hesap

Mevcut iki argümanlı `evaluate(StructuralMassProperties, MotorMassProperties)` metodu imza ve gövde metniyle aynen korunmuştur; deduplication/refactor yapılmadı. Eklenen tek public overload:

```java
public RocketMassProperties evaluate(
    StructuralMassProperties structure,
    MotorMassProperties motor,
    double[] auxiliaryMassesKg,
    double[] auxiliaryCgXGeoM
);
```

İki metot da instance metodudur ve aynı mevcut `RocketMassProperties(double totalMassKg, double totalCgXGeoM)` record'unu döndürür. Yeni production tipi, calculator/result otoritesi, public configuration, field, cache veya history yoktur. Compiled JAR üzerinde `javap`, yalnız bu iki public evaluate metodunu doğruladı.

`m_total = m_structure + m_motor + Σ m_aux`

`x_total = (m_structure*x_structure + m_motor*x_motor + Σ m_aux*x_aux) / m_total`

SI: kg ve m; nose-tip origin, aft-positive x_geo. Structure ve motor hazır sonuç olarak tüketilir; component mass, material, motor depletion/CG/installation yeniden hesaplanmaz. Auxiliary component için sahte BulkMaterial oluşturulmaz. Önce structure+motor, ardından yardımcı girdiler kendi indis sıralarıyla toplanır; her entry aggregate'e bir kez katılır. Aynı kaydı iki kez vermeyi bu düşük-seviye aggregator sahiplik gerekçesiyle deduplicate etmez.

Tüm contributor koordinatları tam eşitse CG tam olarak structural koordinattan döner; büyük koordinatlarda moment overflow'u olsa bile legacy equal-coordinate konvansiyonu korunur. Diğer durumlarda weighted CG ve contributor span invariant'ı kullanılır. Yeni epsilon, cutoff, rounding, clamp veya fallback yoktur.

## Validation ve hatalar

Sıra: iki array için null kontrolü → eşit uzunluk → boşsa **doğrudan eski evaluate metoduna delegasyon** → artan indislerde mass-before-CG validation → aggregate → total mass → total CG → tam contributor span → mevcut result.

| Koşul | Exception / code / field |
|---|---|
| Null array veya farklı uzunluk | Tam `IllegalArgumentException`; sayısal value/code uydurulmaz |
| Auxiliary mass finite değil veya ≤0 | `MassValidationError`, `INVALID_AUXILIARY_MASS`, `auxiliary_masses_kg[i]`, özgün değer |
| Auxiliary CG finite değil | `MassValidationError`, `INVALID_AUXILIARY_CG`, `auxiliary_cg_x_geo_m[i]`, özgün değer |
| Total mass nonfinite | Mevcut `INVALID_TOTAL_MASS`, `total_mass_kg` |
| Total mass ≤0 | Mevcut `NON_POSITIVE_TOTAL_MASS`, `total_mass_kg` |
| Total CG nonfinite | Mevcut `INVALID_TOTAL_CG`, `total_cg_x_geo_m` |
| CG min/max contributor aralığı dışında | Mevcut `TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN`, `total_cg_x_geo_m` |

İlk invalid entry raporlanır; aynı indiste mass önce kontrol edilir. Auxiliary CG için rocket-length/pozitif konum şartı yoktur; negatif finite x_geo kabul edilir. Span, structure/motor ve **bütün** auxiliary CG'leri içerir. Upstream raw record'larına yeni validation politikası eklenmez; eski overload'un özel/error davranışı bağımsız korunur.

Array'ler değiştirilmez, sıralanmaz veya saklanmaz. Calculator stateless; result yalnız iki double tutar. Caller array'lerini sonradan değiştirmek önceki sonucu değiştirmez.

## Analitik ve regresyon kanıtı

| Fixture | Doğrulanan sonuç |
|---|---|
| V&V-01: structure .50/.60; motor .10/.90; auxiliary .08/.40 ve .12/1.00 | Total ≈0.80 kg, moment ≈0.542 kg·m, CG ≈0.6775 m |
| V&V-02 A | V&V-01 ile aynı |
| V&V-02 B: yalnız supplied motor .05/.80 | Total ≈0.75 kg, moment ≈0.492 kg·m, CG ≈0.6560 m; auxiliary array'ler değişmez |
| V&V-03: empty arrays | Real F50 motor-time 0, .354, 1.4, 1.43 s için eski/yeni result **exact equals**; legacy extreme/invalid sonuçları da korunur |
| V&V-04: equal CG | −10, 0, .626619970554718, 1e100 ve Double.MAX_VALUE koordinatlarında tam coordinate equality |
| V&V-05: auxiliary eski span dışında | Structure/motor CG 1/2 m, auxiliary CG −1 veya 4 m; total CG tam −.5 veya 3.5 m, full span içinde |
| V&V-06 | Unsorted array'ler unchanged; post-call mutation result'ı etkilemez; aynı input aynı sonuç |

Analitik ondalık fixture'larda mevcut `RocketMassTest` konvansiyonu olan `abs(expected)*3e-15` assertion toleransı kullanıldı; production toleransı veya yeni numerical policy eklenmedi. Empty-result equality, equal-CG ve tam temsil edilen binary fixture'lar exact assertion kullanır. Span kontrolü doğrudan closed inequality'dir. Source values, invalid metadata (NaN/±Infinity/−0 dahil) ve shape exception type ayrıca doğrulanır.

| Frozen test grupları | Odaklı kanıt |
|---|---|
| MASS23-T01–T04 | Public method reflection, exact result schema, alan yokluğu, paralel production class yokluğu |
| T05/T22 | Empty delegation ve dört gerçek tarihsel F50 mass/CG anchor'ı; legacy extreme/errors |
| T06–T09 | Tek/çoklu/duplicate ordered contribution; supplied motor mass ve installed CG değişimi; weighted aggregate |
| T10/T11/T18 | Exact equal-CG, negatif finite coordinate, full span ve derived span failure |
| T12–T15/T19 | Null/shape önce; sıralı entry validation, mass-before-CG; exception/code/field/value |
| T16/T17 | Upstream ve auxiliary arithmetic kaynaklı mass/moment overflow, NaN, zero/negative total ve error önceliği |
| T20/T21 | Array mutation isolation, deterministic/stateless calculator, primitive-only result |
| T23/T24 | Dar source diff; legacy method birebir korunması; mevcut mass/propulsion/PhysicsEvaluator regression |

Bu gruplar assertion kapsamını belirtir; 24 ayrı JUnit metodu veya ayrı sınıf iddiası değildir. Ek subnormal-positive mass ve yakın fakat eşit olmayan CG testleri yeni cutoff/epsilon olmadığını kontrol eder.

## Çalıştırılan doğrulama

Ortam: Windows 11 amd64; Temurin java/javac **17.0.20.1+1**; Gradle Wrapper **8.12.1** (repository distribution SHA-256 doğrulamalı); mevcut locked JUnit **5.10.0**, launcher **1.10.0**. Yeni dependency/configuration değişikliği yoktur.

İlk ortam hazırlığında Wrapper/cache ve JUnit compile/runtime cache eksikti: sandbox DNS erişimi ve offline cache denemeleri çalıştırılamadı. Mevcut Wrapper ve lock sürümleri ağ erişimiyle cache'e indirildi; fizik/test beklentisi veya build ayarı değiştirilmedi. Sonuçlar bu ortam hataları PASS sayılmadan aşağıda ayrılır.

- RED: overload eklenmeden `AuxiliaryMassTest` compileTestJava **exit 1**, eksik dört-argümanlı metot nedeniyle 32 compile error; testler o aşamada çalışmadı.
- İlk GREEN: AuxiliaryMassTest **26 test**, failure/error/skipped **0**, exit **0**. Son incelemede aynı testlere auxiliary kaynaklı overflow/zero-total assertion'ları eklendi; eski assertion/tolerance değiştirilmedi.

Son seçici temiz doğrulama, Core dizininden:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir '<build-dir>' clean assemble test --tests org.roketsim.core.AuxiliaryMassTest --tests org.roketsim.core.RocketMassTest --tests org.roketsim.core.StructuralMassTest --tests org.roketsim.core.MotorPropertiesTest --tests org.roketsim.core.PhysicsEvaluatorTest verifyProductionDependencies --offline --no-daemon
```

| Suite / kapı | Gerçek sonuç |
|---|---|
| AuxiliaryMassTest | **26 passed** |
| RocketMassTest (değiştirilmedi) | **7 passed** |
| StructuralMassTest (değiştirilmedi) | **23 passed** |
| MotorPropertiesTest (değiştirilmedi) | **7 passed** |
| PhysicsEvaluatorTest (değiştirilmedi) | **13 passed** |
| Seçilen toplam | **76 test, 0 failure/error/skipped**, exit **0**; XML raporlarından sayıldı |
| Java17 clean assemble / test compile | Başarılı; production/sources/Javadoc JAR'ları üretildi; exit **0** |
| verifyProductionDependencies | Başarılı; exit **0**, harici production dependency yok |
| `javap` packaged calculator | Orijinal ve onaylı overload dışında public evaluate yok; exit **0** |
| `jdeps --print-module-deps .../roketsim-core-0.1.0.jar` | Yalnız **java.base**, exit **0** |

Test raporları `<local-evidence>` altındadır; build çıktıları repository'ye eklenmez. Javadoc mevcut documentation borcuyla gösterim limitinde **100 warning** üretti; tam warning envanteri iddiası değildir, build başarısını engellemedi. Tam repository/Java/Python/parity/trajectory kampanyası çalıştırılmadı; bu gate için selektif kapsam yeterlidir ve talep edilen sınırdır.

## NAT-024 sınırı ve kabul

NAT-023 yalnız açık axial mass/CG aggregate'idir. Donanım sahipliği/double-accounting, vehicle veya flight configuration'a tanımları bağlamak **NAT-024** kapsamıdır. PhysicsEvaluator hâlâ eski iki-argümanlı yolu çağırır; NAT-023 bu yolu auxiliary girdilerle otomatik değiştirmez. Attached recovery donanımı explicit auxiliary input olarak verilirse mass katkısı aynen kalır; deployment, separation veya mass-removal mantığı yoktur.

Inertia, off-axis/rotational physics, staging, recovery dynamics, geometry-derived auxiliary mass, yeni mass-evolution law veya hidden default yoktur. Scope/API/material bir uyuşmazlık gözlenmedi. Orijinal metot metni baseline ile karşılaştırılarak aynı bulundu; toplam rocket mass için tek production calculator ve tek mevcut result schema korunur. Yalnız bu production dosyası, yeni test ve bu kayıt yerel milestone commit'ine dahil edilir; push/NAT-024 başlatılması yetkili değildir.

Kabul önerisi: **NAT-023 PASS**; seçici verification gate'leri geçti. Yerel commit SHA'sı ve son Git durumu final görev raporunda kaydedilir.

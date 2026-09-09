# Simülasyon Motoru

## Güncel V1 Kapsamı

- `PhysicsEvaluator3DOF`, sağlanan `(time, state)` için kabul edilmiş çevre, akış,
  itki, kütle, drag ve dinamik authority'lerini orkestre eder.
- RK4 türev yolu, trial sonuçlarını endpoint authority'si olarak cache'lemeden dört
  sayısal stage'in tamamında anlık fiziği yeniden hesaplar.
- `SimulationEngine3DOF`; ignition durumunu kurar, sabit RK4 candidate'larını
  ilerletir, olayları tarar, kabul edilmiş endpoint physics'i değerlendirir ve
  geçmişi kaydeder.
- Candidate state ancak terminal-event handling sonrasında kabul edilir; interior
  terminal candidate yörünge akışına yükseltilmez.
- NAT-018, kabul edilmiş curve-end ve doğrusal bracket-crossing semantiğiyle BURNOUT,
  APOGEE ve terminal GROUND occurrence'larını raporlar.
- `FlightRecorder3DOF`, append-only kabul edilmiş sample'ları ve localized event'leri
  ayrı kronolojik akışlar olarak tutar.
- `SimulationExecution3DOF`; recorded data, termination reason ve yürütülen
  candidate-step sayısını içerir.
- `SimulationResult3DOF`, adlandırılmış ve generic event erişimleri sunan
  değiştirilemez execution sorgu facade'ıdır.
- `last_accepted_sample`, tahmin edilmiş interior terminal state'ten ayrı kalır.
- `termination_time_s`, terminal-event zamanını veya son kabul edilmiş noktayı izler;
  `simulation_duration_s` ilk ignition sample'ından ölçülür.
- V1 simülasyon zamanı propulsion ignition anında başlar.
- Sürüm `1.0` yerel JSON/CLI sınırı, tek stdin isteğini accepted simülasyon
  zincirine bağlar ve tek stdout yanıtı üretir.
- Java 17 istemcileri bu sınırı `ProcessBuilder` ile kullanabilir; Python motoru
  tek fizik otoritesi olarak kalır ve ağ/sunucu katmanı çalıştırılmaz.

## Kapsam Dışı / Planlanan

- Fırlatma öncesi pad/contact reaksiyonu ve ayrı Liftoff olayı ertelenmiştir.
- ON_GUIDE dinamiği, launch-rail kısıtları ve rail clear ertelenmiştir.
- Recovery ve deployment ertelenmiştir.
- `flight_time_s`, Liftoff kabul edilmiş bir authority kazanana kadar ertelenmiştir.
- Yerel V1 JSON/CLI sınırı dışındaki geniş frontend/public-facade entegrasyonu
  ertelenmiştir.
- Genel amaçlı dosya serialization, export, plotting ve reporting API'leri
  ertelenmiştir.
- V1 dış sözleşmesi yalnız sürümlü doğrulanmış demo araç preset'ini destekler;
  keyfî araç JSON yapılandırması henüz public değildir.
- Ağ tabanlı API veya servis güncel mimarinin parçası değildir.

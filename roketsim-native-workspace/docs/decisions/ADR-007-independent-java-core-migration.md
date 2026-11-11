# ADR-007 — Independent Java Core Migration

## Karar

J-000/J-001 kapsamında RoketSim Native'in kabul edilmiş Python 3DOF davranışı,
ayrı bir Java 17 Gradle kütüphanesine kontrollü paketler halinde taşınacaktır.
Java Core `org.roketsim.core` namespace'ini kullanır ve production'da yalnız JDK
gerektirir. OpenRocket, Swing, frontend modelleri, Python, JSON/CLI ve ağ servisleri
Core bağımlılığı değildir. Nihai frontend sınırı doğrudan Java nesne API'sidir.

## Otorite ve kapsam

Python implementation, testler ve controlled model kararları migration referansıdır.
Dil geçişi yeni fizik, tolerans veya default üretmez. WORLD ENU, BODY z_B ve ayrı
nose-tip x_geo convention'ları ile SI korunur. 3DOF doğrulanmadan 6DOF eklenmez.
Tek library içinde math, units, environment, flightconditions, geometry, materials,
mass, propulsion, aerodynamics, dynamics, numerics ve simulation paket sınırları
J-000 hedefidir; J-001 yalnız math/vector ve metadata temelini uygular.

## Tarihsel kararlarla ilişki

ADR-001 ve ADR-003 tarihsel içerikleriyle korunur. Bu karar Java migration hedefini
belgeler; Python runtime ve JSON/CLI bugün kaldırılmaz. Retirement ancak Java
subsystem ve tam trajectory parity, full regression, frontend direct-API consumer
ve Python gerektirmeyen dağıtım doğrulamalarından sonra ayrı milestone ile yapılır.
Referans Python kaynak/test/provenance kayıtları kalıcı olarak korunacaktır.

## Sonuçlar

Java library build'i Gradle Wrapper 8.12.1, JDK toolchain/release 17 ve UTF-8 kullanır.
Frontend adaptörü iki tarafın modellerini bilebilir; Core frontend türlerini bilmez.
Core lisans seçimi ve artifact yayın deposu ayrıca kararlaştırılacaktır. J-001
artifact metadata'sı yayın hazırlığıdır; lisans veya yayın yetkisi ilan etmez.

# ADR-001 Native Engine Python

## Karar

RoketSim Native fizik motoru minimum Python 3.12 desteği ve Python 3.12.x geliştirme baseline'ı ile, OpenRocket Java sınıflarından ve kullanıcı arayüzünden bağımsız geliştirilecektir.

## Gerekçe

Fizik çekirdeğinin bağımsız test edilmesi ve ileride farklı istemcilerden kullanılabilmesi gerekir.

## Sonuçlar

Python sürüm politikası `>=3.12` olacaktır. Geliştirme için proje-local `.venv` kullanılacak; mevcut MGM Conda/WSL ortamları kullanılmayacak veya değiştirilmeyecektir. Java veya UI bağımlılıkları physics core'a alınmayacaktır.

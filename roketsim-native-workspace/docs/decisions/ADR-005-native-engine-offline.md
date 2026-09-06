# ADR-005 Native Engine Offline

## Karar

Normal Native fizik yürütmesi internet veya ağ bağlantısı gerektirmeyecektir.

## Gerekçe

Simülasyonun çevrimdışı, güvenilir ve yeniden üretilebilir çalışması gerekir.

## Sonuçlar

Runtime physics path ağ servisine bağlanmayacak; gerekli girdiler yerel ve açık sözleşmelerle sağlanacaktır.


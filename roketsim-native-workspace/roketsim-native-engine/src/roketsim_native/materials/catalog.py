"""Frozen ilk demo kataloğu; bu seçenekler component ataması/default değildir."""

from typing import Final

from roketsim_native.materials.models import BulkMaterial

__all__ = ("CARDBOARD", "POLYSTYRENE")

CARDBOARD: Final[BulkMaterial] = BulkMaterial(name="Cardboard", density_kg_m3=680.0)
POLYSTYRENE: Final[BulkMaterial] = BulkMaterial(name="Polystyrene", density_kg_m3=1050.0)

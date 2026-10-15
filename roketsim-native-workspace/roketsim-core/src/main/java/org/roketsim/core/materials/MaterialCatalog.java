package org.roketsim.core.materials;

/** Accepted Python materials/catalog.py frozen demo seçenekleri; component default'u değil. */
public final class MaterialCatalog {
    private MaterialCatalog() { }
    /** Kaynak katalog kimliği Cardboard; accepted density 680 kg/m³. */
    public static final BulkMaterial CARDBOARD=new BulkMaterial("Cardboard",680.0);
    /** Kaynak katalog kimliği Polystyrene; accepted density 1050 kg/m³. */
    public static final BulkMaterial POLYSTYRENE=new BulkMaterial("Polystyrene",1050.0);
}

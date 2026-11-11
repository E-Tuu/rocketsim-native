package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param finCount integral adet; floating-point 4.0 ve bool sessizce int yapılmaz
 * @param rootChordM axial root chord (m)
 * @param tipChordM axial tip chord (m); zero triangular limit geçerli
 * @param semiSpanM body yüzeyinden fin tip'e radial mesafe (m)
 * @param tipLeadingEdgeOffsetXM signed tip LE offset (m), positive tailward
 * @param rootLeadingEdgeXGeoM nose tip kaynaklı absolute root LE (m)
 * @param thicknessM uniform solid plate kalınlığı (m)
 * @param crossSection açık FinCrossSection selector; planform şekli değildir
 * @param angularArrangement açık FinAngularArrangement selector
 */
public record TrapezoidalFinSetGeometry(Object finCount, double rootChordM, double tipChordM, double semiSpanM, double tipLeadingEdgeOffsetXM, double rootLeadingEdgeXGeoM, double thicknessM, Object crossSection, Object angularArrangement) { }

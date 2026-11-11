package org.roketsim.core.geometry;

/** Tek geometry source ve bütün kabul edilmiş derived SI çıktılar.
 * Constructor doğrulamaz; density/mass/motor-runtime otoritesi değildir.
 * @param source immutable raw tasarım
 * @param noseStartXGeoM Python nose_start_x_geo_m karşılığı
 * @param noseEndXGeoM Python nose_end_x_geo_m karşılığı
 * @param bodyStartXGeoM Python body_start_x_geo_m karşılığı
 * @param bodyEndXGeoM Python body_end_x_geo_m karşılığı
 * @param finRootLeadingEdgeXGeoM Python fin_root_leading_edge_x_geo_m karşılığı
 * @param finRootTrailingEdgeXGeoM Python fin_root_trailing_edge_x_geo_m karşılığı
 * @param finTipLeadingEdgeXGeoM Python fin_tip_leading_edge_x_geo_m karşılığı
 * @param finTipTrailingEdgeXGeoM Python fin_tip_trailing_edge_x_geo_m karşılığı
 * @param overallLengthM Python overall_length_m karşılığı
 * @param referenceDiameterM Python reference_diameter_m karşılığı
 * @param referenceLengthM Python reference_length_m karşılığı
 * @param referenceAreaM2 Python reference_area_m2 karşılığı
 * @param noseMaterialVolumeM3 Python nose_material_volume_m3 karşılığı
 * @param noseVolumeCentroidXGeoM Python nose_volume_centroid_x_geo_m karşılığı
 * @param bodyMaterialVolumeM3 Python body_material_volume_m3 karşılığı
 * @param bodyVolumeCentroidXGeoM Python body_volume_centroid_x_geo_m karşılığı
 * @param finSetMaterialVolumeM3 Python fin_set_material_volume_m3 karşılığı
 * @param finSetVolumeCentroidXGeoM Python fin_set_volume_centroid_x_geo_m karşılığı
 * @param bodyInnerDiameterM Python body_inner_diameter_m karşılığı
 * @param motorMountInnerDiameterM Python motor_mount_inner_diameter_m karşılığı
 * @param motorMountOuterDiameterM Python motor_mount_outer_diameter_m karşılığı
 * @param motorMountStartXGeoM Python motor_mount_start_x_geo_m karşılığı
 * @param motorMountEndXGeoM Python motor_mount_end_x_geo_m karşılığı
 * @param frontCenteringRingStartXGeoM Python front_centering_ring_start_x_geo_m karşılığı
 * @param frontCenteringRingEndXGeoM Python front_centering_ring_end_x_geo_m karşılığı
 * @param rearCenteringRingStartXGeoM Python rear_centering_ring_start_x_geo_m karşılığı
 * @param rearCenteringRingEndXGeoM Python rear_centering_ring_end_x_geo_m karşılığı
 * @param motorAftReferenceXGeoM Python motor_aft_reference_x_geo_m karşılığı
 * @param motorMountMaterialVolumeM3 Python motor_mount_material_volume_m3 karşılığı
 * @param motorMountVolumeCentroidXGeoM Python motor_mount_volume_centroid_x_geo_m karşılığı
 * @param centeringRingPairMaterialVolumeM3 Python centering_ring_pair_material_volume_m3 karşılığı
 * @param centeringRingPairVolumeCentroidXGeoM Python centering_ring_pair_volume_centroid_x_geo_m karşılığı
 * @param referenceGeometryPolicy Python reference_geometry_policy karşılığı
 * @param aerodynamicLengthM Python aerodynamic_length_m karşılığı
 * @param maxExternalAirframeDiameterM Python max_external_airframe_diameter_m karşılığı
 * @param axisymmetricBodyLengthM Python axisymmetric_body_length_m karşılığı
 * @param noseWettedAreaM2 Python nose_wetted_area_m2 karşılığı
 * @param bodyWettedAreaM2 Python body_wetted_area_m2 karşılığı
 * @param noseFrontalAreaM2 Python nose_frontal_area_m2 karşılığı
 * @param airframeAftBaseAreaM2 Python airframe_aft_base_area_m2 karşılığı
 * @param noseFinenessRatio Python nose_fineness_ratio karşılığı
 * @param noseHalfAngleRad Python nose_half_angle_rad karşılığı
 * @param finPlanformAreaPerFinM2 Python fin_planform_area_per_fin_m2 karşılığı
 * @param finMeanAerodynamicChordM Python fin_mean_aerodynamic_chord_m karşılığı
 * @param finLeadingEdgeSweepAngleRad Python fin_leading_edge_sweep_angle_rad karşılığı
 * @param finCrossSection Python fin_cross_section karşılığı
 * @param noseAxialLengthM Python nose_axial_length_m karşılığı
 * @param finMidchordSweepAngleRad Python fin_midchord_sweep_angle_rad karşılığı
 * @param finMeanAerodynamicChordSpanwiseLocationM Python fin_mean_aerodynamic_chord_spanwise_location_m karşılığı
 * @param finMeanAerodynamicChordLeadingEdgeXGeoM Python fin_mean_aerodynamic_chord_leading_edge_x_geo_m karşılığı
 * @param finAspectRatio Python fin_aspect_ratio karşılığı
 * @param finBodyRadiusAtRootM Python fin_body_radius_at_root_m karşılığı
 * @param finAngularArrangement Python fin_angular_arrangement karşılığı
 */
public record ResolvedRocketGeometry(SingleStageRocketGeometry source,
        double noseStartXGeoM,
        double noseEndXGeoM,
        double bodyStartXGeoM,
        double bodyEndXGeoM,
        double finRootLeadingEdgeXGeoM,
        double finRootTrailingEdgeXGeoM,
        double finTipLeadingEdgeXGeoM,
        double finTipTrailingEdgeXGeoM,
        double overallLengthM,
        double referenceDiameterM,
        double referenceLengthM,
        double referenceAreaM2,
        double noseMaterialVolumeM3,
        double noseVolumeCentroidXGeoM,
        double bodyMaterialVolumeM3,
        double bodyVolumeCentroidXGeoM,
        double finSetMaterialVolumeM3,
        double finSetVolumeCentroidXGeoM,
        double bodyInnerDiameterM,
        double motorMountInnerDiameterM,
        double motorMountOuterDiameterM,
        double motorMountStartXGeoM,
        double motorMountEndXGeoM,
        double frontCenteringRingStartXGeoM,
        double frontCenteringRingEndXGeoM,
        double rearCenteringRingStartXGeoM,
        double rearCenteringRingEndXGeoM,
        double motorAftReferenceXGeoM,
        double motorMountMaterialVolumeM3,
        double motorMountVolumeCentroidXGeoM,
        double centeringRingPairMaterialVolumeM3,
        double centeringRingPairVolumeCentroidXGeoM,
        ReferenceGeometryPolicy referenceGeometryPolicy,
        double aerodynamicLengthM,
        double maxExternalAirframeDiameterM,
        double axisymmetricBodyLengthM,
        double noseWettedAreaM2,
        double bodyWettedAreaM2,
        double noseFrontalAreaM2,
        double airframeAftBaseAreaM2,
        double noseFinenessRatio,
        double noseHalfAngleRad,
        double finPlanformAreaPerFinM2,
        double finMeanAerodynamicChordM,
        double finLeadingEdgeSweepAngleRad,
        FinCrossSection finCrossSection,
        double noseAxialLengthM,
        double finMidchordSweepAngleRad,
        double finMeanAerodynamicChordSpanwiseLocationM,
        double finMeanAerodynamicChordLeadingEdgeXGeoM,
        double finAspectRatio,
        double finBodyRadiusAtRootM,
        FinAngularArrangement finAngularArrangement) { }

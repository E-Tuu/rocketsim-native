package org.roketsim.core.geometry;

import java.math.BigInteger;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Python resolver.py sıralı validation ve geometry hesapları; mass/aero fiziği yoktur. */
public final class GeometryResolver {
    /** Parametresiz stateless resolver. */
    public GeometryResolver() { }
    private static GeometryValidationError error(String code,String field,Object value) {
        return new GeometryValidationError(code,field,value);
    }
    private static void positive(double value,String name,String code) {
        if (value<=0) { throw error(code,name,value); }
    }
    private static void derivedPositive(double value,String name,String code) {
        if (!Double.isFinite(value) || value<=0) { throw error(code,name,value); }
    }
    private static void volume(double value,String name,String code) {
        requireFinite(value,name); positive(value,name,code);
    }
    private static void centroid(double value,String name,double lower,double upper,String code) {
        requireFinite(value,name);
        if (!(lower<=value && value<=upper)) { throw error(code,name,value); }
    }
    private static boolean integer(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof BigInteger;
    }
    private static double count(Object value) {
        if (!integer(value)) {
            if (value instanceof Number number) { requireFinite(number.doubleValue(),"fins.fin_count"); }
            else if (!(value instanceof Boolean)) { throw new IllegalArgumentException("fins.fin_count must be numerical"); }
        }
        if (!integer(value) || ((Number)value).doubleValue()<3) {
            throw error("INVALID_FIN_COUNT","fins.fin_count",value);
        }
        return ((Number)value).doubleValue();
    }
    /** @param geometry tek immutable raw source
     * @return bütün accepted material-volume, centroid, layout ve reference çıktıları */
    public ResolvedRocketGeometry resolve(SingleStageRocketGeometry geometry) {
        var fins=geometry.fins();
        if (!(geometry.referenceGeometryPolicy() instanceof ReferenceGeometryPolicy)) {
            throw error("INVALID_REFERENCE_GEOMETRY_POLICY","reference_geometry_policy",geometry.referenceGeometryPolicy());
        }
        if (!(fins.crossSection() instanceof FinCrossSection)) {
            throw error("INVALID_FIN_CROSS_SECTION","fins.cross_section",fins.crossSection());
        }
        if (!(fins.angularArrangement() instanceof FinAngularArrangement)) {
            throw error("INVALID_FIN_ANGULAR_ARRANGEMENT","fins.angular_arrangement",fins.angularArrangement());
        }
        String[] positiveNames={"airframe_diameter_m","nose.length_m","body.length_m","fins.root_chord_m","fins.semi_span_m","fins.thickness_m"};
        double[] dimensions={geometry.airframeDiameterM(),geometry.nose().lengthM(),geometry.body().lengthM(),fins.rootChordM(),fins.semiSpanM(),fins.thicknessM()};
        for (int i=0;i<dimensions.length;i++) { requireFinite(dimensions[i],positiveNames[i]); }
        requireFinite(fins.tipChordM(),"fins.tip_chord_m");
        requireFinite(fins.tipLeadingEdgeOffsetXM(),"fins.tip_leading_edge_offset_x_m");
        requireFinite(fins.rootLeadingEdgeXGeoM(),"fins.root_leading_edge_x_geo_m");
        for (int i=0;i<dimensions.length;i++) { positive(dimensions[i],positiveNames[i],"NON_POSITIVE_DIMENSION"); }
        if (fins.tipChordM()<0) { throw error("NEGATIVE_TIP_CHORD","fins.tip_chord_m",fins.tipChordM()); }
        double finCount=count(fins.finCount());
        double noseEnd=geometry.nose().lengthM();
        double bodyEnd=requireFinite(noseEnd+geometry.body().lengthM(),"body_end_x_geo_m");
        double rootLe=fins.rootLeadingEdgeXGeoM();
        double rootTe=requireFinite(rootLe+fins.rootChordM(),"fin_root_trailing_edge_x_geo_m");
        double tipLe=requireFinite(rootLe+fins.tipLeadingEdgeOffsetXM(),"fin_tip_leading_edge_x_geo_m");
        double tipTe=requireFinite(tipLe+fins.tipChordM(),"fin_tip_trailing_edge_x_geo_m");
        if (rootLe<noseEnd || rootTe>bodyEnd) { throw error("FIN_ROOT_OUTSIDE_BODY",null,null); }
        if (tipLe<0 || tipTe<0) { throw error("FIN_EXTENDS_BEFORE_NOSE",null,null); }
        double diameter=geometry.airframeDiameterM();
        double area=requireFinite((Math.PI/4.0)*diameter*diameter,"reference_area_m2");
        if (area<=0) { throw new IllegalArgumentException("reference_area_m2 must be positive; numerical underflow"); }
        var nose=geometry.nose();
        Double noseThickness=nose.wallThicknessM();
        double bodyThickness=geometry.body().wallThicknessM();
        if (noseThickness!=null) { requireFinite(noseThickness,"nose.wall_thickness_m"); }
        requireFinite(bodyThickness,"body.wall_thickness_m");
        if (!(nose.constructionMode() instanceof NoseConstructionMode)) {
            throw error("INVALID_NOSE_CONSTRUCTION","nose.construction_mode",nose.constructionMode());
        }
        double radius=diameter/2.0;
        positive(bodyThickness,"body.wall_thickness_m","NON_POSITIVE_WALL_THICKNESS");
        if (bodyThickness>=radius) { throw error("BODY_WALL_TOO_THICK","body.wall_thickness_m",bodyThickness); }
        double outerVolume=requireFinite(Math.PI*radius*radius*noseEnd/3.0,"nose_outer_volume_m3");
        double outerCentroid=3.0*(noseEnd/4.0);
        double noseVolume,noseCentroid;
        if (nose.constructionMode()==NoseConstructionMode.SOLID) {
            if (noseThickness!=null) { throw error("UNEXPECTED_WALL_THICKNESS","nose.wall_thickness_m",noseThickness); }
            noseVolume=outerVolume; noseCentroid=outerCentroid;
        } else {
            if (noseThickness==null) { throw error("MISSING_WALL_THICKNESS","nose.wall_thickness_m",null); }
            positive(noseThickness,"nose.wall_thickness_m","NON_POSITIVE_WALL_THICKNESS");
            double slant=requireFinite(Math.hypot(noseEnd,radius),"nose_slant_length_m");
            double limit=noseEnd*radius/slant;
            if (noseThickness>=limit) { throw error("NOSE_SHELL_TOO_THICK","nose.wall_thickness_m",noseThickness); }
            double deltaX=requireFinite(noseThickness*slant/radius,"nose_inner_tip_offset_m");
            double deltaR=requireFinite(noseThickness*slant/noseEnd,"nose_inner_radius_reduction_m");
            double innerLength=noseEnd-deltaX,innerRadius=radius-deltaR;
            if (innerLength<=0 || innerRadius<=0) { throw error("NOSE_SHELL_TOO_THICK","nose.wall_thickness_m",noseThickness); }
            double innerVolume=requireFinite(Math.PI*innerRadius*innerRadius*innerLength/3.0,"nose_inner_volume_m3");
            noseVolume=requireFinite(outerVolume-innerVolume,"nose_material_volume_m3");
            positive(noseVolume,"nose_material_volume_m3","INVALID_DERIVED_VOLUME");
            double innerCentroid=deltaX+3.0*(innerLength/4.0);
            noseCentroid=(outerVolume*outerCentroid-innerVolume*innerCentroid)/noseVolume;
        }
        double bodyInnerRadius=radius-bodyThickness;
        double bodyVolume=Math.PI*(radius*radius-bodyInnerRadius*bodyInnerRadius)*geometry.body().lengthM();
        double bodyCentroid=noseEnd+geometry.body().lengthM()/2.0;
        double rootChord=fins.rootChordM(),tipChord=fins.tipChordM();
        double finArea=(rootChord+tipChord)*fins.semiSpanM()/2.0;
        // Python int*float dönüşümü taşarsa numerical programming error korunur.
        if (!Double.isFinite(finCount)) { throw new ArithmeticException("int too large to convert to float"); }
        double finVolume=finCount*finArea*fins.thicknessM();
        double finCentroid=rootLe+(rootChord*rootChord+rootChord*tipChord+tipChord*tipChord
            +fins.tipLeadingEdgeOffsetXM()*(rootChord+2.0*tipChord))/(3.0*(rootChord+tipChord));
        volume(noseVolume,"nose_material_volume_m3","INVALID_DERIVED_VOLUME");
        volume(bodyVolume,"body_material_volume_m3","INVALID_DERIVED_VOLUME");
        volume(finVolume,"fin_set_material_volume_m3","INVALID_DERIVED_VOLUME");
        centroid(noseCentroid,"nose_volume_centroid_x_geo_m",0,noseEnd,"INVALID_DERIVED_CENTROID");
        centroid(bodyCentroid,"body_volume_centroid_x_geo_m",noseEnd,bodyEnd,"INVALID_DERIVED_CENTROID");
        centroid(finCentroid,"fin_set_volume_centroid_x_geo_m",Math.min(rootLe,tipLe),Math.max(rootTe,tipTe),"INVALID_DERIVED_CENTROID");
        var attachment=geometry.motorAttachment();
        var mount=attachment.mountTube();
        double ringThickness=attachment.centeringRings().axialThicknessM();
        String[] mountNames={"motor_attachment.mount_tube.length_m","motor_attachment.mount_tube.inner_diameter_m",
            "motor_attachment.mount_tube.wall_thickness_m","motor_attachment.centering_rings.axial_thickness_m"};
        double[] mountValues={mount.lengthM(),mount.innerDiameterM(),mount.wallThicknessM(),ringThickness};
        String[] mountCodes={"NON_POSITIVE_MOUNT_LENGTH","NON_POSITIVE_MOUNT_INNER_DIAMETER","NON_POSITIVE_MOUNT_WALL_THICKNESS","NON_POSITIVE_CENTERING_RING_THICKNESS"};
        for (int i=0;i<mountValues.length;i++) { requireFinite(mountValues[i],mountNames[i]); }
        requireFinite(mount.aftRecessM(),"motor_attachment.mount_tube.aft_recess_m");
        requireFinite(attachment.motorOverhangM(),"motor_attachment.motor_overhang_m");
        for (int i=0;i<mountValues.length;i++) { positive(mountValues[i],mountNames[i],mountCodes[i]); }
        if (mount.aftRecessM()<0) { throw error("NEGATIVE_MOUNT_AFT_RECESS","motor_attachment.mount_tube.aft_recess_m",mount.aftRecessM()); }
        double bodyInnerDiameter=requireFinite(diameter-2.0*bodyThickness,"body_inner_diameter_m");
        positive(bodyInnerDiameter,"body_inner_diameter_m","INVALID_BODY_INNER_DIAMETER");
        double mountOuter=requireFinite(mount.innerDiameterM()+2.0*mount.wallThicknessM(),"motor_mount_outer_diameter_m");
        if (mountOuter>=bodyInnerDiameter) { throw error("MOTOR_MOUNT_TOO_LARGE_FOR_BODY","motor_mount_outer_diameter_m",mountOuter); }
        double mountEnd=requireFinite(bodyEnd-mount.aftRecessM(),"motor_mount_end_x_geo_m");
        double mountStart=requireFinite(mountEnd-mount.lengthM(),"motor_mount_start_x_geo_m");
        if (mountStart<noseEnd || mountEnd>bodyEnd) { throw error("MOTOR_MOUNT_OUTSIDE_BODY","motor_mount_start_x_geo_m",mountStart); }
        if (2.0*ringThickness>mount.lengthM()) { throw error("CENTERING_RINGS_OVERLAP","motor_attachment.centering_rings.axial_thickness_m",ringThickness); }
        if (attachment.motorOverhangM()<-mount.lengthM()) { throw error("MOTOR_AFT_REFERENCE_BEFORE_MOUNT","motor_attachment.motor_overhang_m",attachment.motorOverhangM()); }
        double aftReference=requireFinite(mountEnd+attachment.motorOverhangM(),"motor_aft_reference_x_geo_m");
        double frontEnd=mountStart+ringThickness,rearStart=mountEnd-ringThickness;
        double mountOuterRadius=mountOuter/2.0,mountInnerRadius=mount.innerDiameterM()/2.0;
        double mountVolume=Math.PI*(mountOuterRadius*mountOuterRadius-mountInnerRadius*mountInnerRadius)*mount.lengthM();
        double ringOuterRadius=bodyInnerDiameter/2.0;
        double oneRingVolume=Math.PI*(ringOuterRadius*ringOuterRadius-mountOuterRadius*mountOuterRadius)*ringThickness;
        double pairVolume=2.0*oneRingVolume;
        double mountCentroid=(mountStart+mountEnd)/2.0;
        double frontCentroid=mountStart+ringThickness/2.0,rearCentroid=mountEnd-ringThickness/2.0;
        double pairCentroid=(frontCentroid+rearCentroid)/2.0;
        volume(mountVolume,"motor_mount_material_volume_m3","INVALID_MOUNT_VOLUME");
        volume(oneRingVolume,"one_centering_ring_material_volume_m3","INVALID_RING_VOLUME");
        volume(pairVolume,"centering_ring_pair_material_volume_m3","INVALID_RING_VOLUME");
        centroid(mountCentroid,"motor_mount_volume_centroid_x_geo_m",mountStart,mountEnd,"INVALID_MOUNT_CENTROID");
        centroid(pairCentroid,"centering_ring_pair_volume_centroid_x_geo_m",mountStart,mountEnd,"INVALID_RING_CENTROID");
        double overall=Math.max(bodyEnd,Math.max(rootTe,tipTe));
        double noseWetted=Math.PI*radius*Math.hypot(noseEnd,radius);
        double bodyWetted=2.0*Math.PI*radius*geometry.body().lengthM();
        double frontal=Math.PI*radius*radius,fineness=noseEnd/diameter,halfAngle=Math.atan2(radius,noseEnd);
        double mac=(2.0/3.0)*(rootChord+tipChord-rootChord*tipChord/(rootChord+tipChord));
        double sweep=Math.atan2(fins.tipLeadingEdgeOffsetXM(),fins.semiSpanM());
        double midOffset=fins.tipLeadingEdgeOffsetXM()+(tipChord-rootChord)/2.0;
        double midSweep=Math.atan2(midOffset,fins.semiSpanM());
        double macY=fins.semiSpanM()*(rootChord+2.0*tipChord)/(3.0*(rootChord+tipChord));
        double macLe=rootLe+fins.tipLeadingEdgeOffsetXM()*macY/fins.semiSpanM();
        double aspect=2.0*fins.semiSpanM()*fins.semiSpanM()/finArea;
        String[] derivedNames={"aerodynamic_length_m","max_external_airframe_diameter_m","axisymmetric_body_length_m",
            "nose_wetted_area_m2","body_wetted_area_m2","nose_frontal_area_m2","airframe_aft_base_area_m2",
            "nose_fineness_ratio","fin_planform_area_per_fin_m2","fin_mean_aerodynamic_chord_m","nose_axial_length_m",
            "fin_mean_aerodynamic_chord_spanwise_location_m","fin_mean_aerodynamic_chord_leading_edge_x_geo_m","fin_aspect_ratio","fin_body_radius_at_root_m"};
        double[] derivedValues={overall,diameter,bodyEnd,noseWetted,bodyWetted,frontal,frontal,fineness,finArea,mac,noseEnd,macY,macLe,aspect,radius};
        String[] derivedCodes={"INVALID_AERODYNAMIC_LENGTH","INVALID_MAX_EXTERNAL_AIRFRAME_DIAMETER","INVALID_AXISYMMETRIC_BODY_LENGTH",
            "INVALID_NOSE_WETTED_AREA","INVALID_BODY_WETTED_AREA","INVALID_NOSE_FRONTAL_AREA","INVALID_AIRFRAME_AFT_BASE_AREA",
            "INVALID_NOSE_FINENESS_RATIO","INVALID_FIN_PLANFORM_AREA","INVALID_FIN_MEAN_AERODYNAMIC_CHORD","INVALID_NOSE_AXIAL_LENGTH",
            "INVALID_FIN_MAC_SPANWISE_LOCATION","INVALID_FIN_MAC_LEADING_EDGE_POSITION","INVALID_FIN_ASPECT_RATIO","INVALID_FIN_BODY_RADIUS"};
        for (int i=0;i<derivedValues.length;i++) { derivedPositive(derivedValues[i],derivedNames[i],derivedCodes[i]); }
        if (!Double.isFinite(halfAngle) || !(0<halfAngle && halfAngle<Math.PI/2.0)) { throw error("INVALID_NOSE_HALF_ANGLE","nose_half_angle_rad",halfAngle); }
        if (!Double.isFinite(sweep)) { throw error("INVALID_FIN_SWEEP_ANGLE","fin_leading_edge_sweep_angle_rad",sweep); }
        if (!Double.isFinite(midSweep)) { throw error("INVALID_FIN_MIDCHORD_SWEEP_ANGLE","fin_midchord_sweep_angle_rad",midSweep); }
        return new ResolvedRocketGeometry(geometry,0,noseEnd,noseEnd,bodyEnd,rootLe,rootTe,tipLe,tipTe,
            overall,diameter,diameter,area,noseVolume,noseCentroid,bodyVolume,bodyCentroid,finVolume,finCentroid,
            bodyInnerDiameter,mount.innerDiameterM(),mountOuter,mountStart,mountEnd,mountStart,frontEnd,rearStart,mountEnd,
            aftReference,mountVolume,mountCentroid,pairVolume,pairCentroid,(ReferenceGeometryPolicy)geometry.referenceGeometryPolicy(),
            overall,diameter,bodyEnd,noseWetted,bodyWetted,frontal,frontal,fineness,halfAngle,finArea,mac,sweep,(FinCrossSection)fins.crossSection(),
            noseEnd,midSweep,macY,macLe,aspect,radius,(FinAngularArrangement)fins.angularArrangement());
    }
}

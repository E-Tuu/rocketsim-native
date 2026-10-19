package org.roketsim.core.simulation;

/** Four mandatory NAT-018 policies, validated by accepted enum type. */
public record FlightEventDetectionProfile3DOF(Object burnoutDetectionModel,Object apogeeDetectionModel,
        Object groundReferenceModel,Object localizationModel) {
    public FlightEventDetectionProfile3DOF {
        validate(burnoutDetectionModel,apogeeDetectionModel,groundReferenceModel,localizationModel);
    }
    static void validate(Object burnout,Object apogee,Object ground,Object localization) {
        if(!(burnout instanceof BurnoutDetectionModel))throw new EventDetectionError("UNSUPPORTED_BURNOUT_DETECTION_MODEL","burnout_detection_model",burnout);
        if(!(apogee instanceof ApogeeDetectionModel))throw new EventDetectionError("UNSUPPORTED_APOGEE_DETECTION_MODEL","apogee_detection_model",apogee);
        if(!(ground instanceof GroundReferenceModel))throw new EventDetectionError("UNSUPPORTED_GROUND_REFERENCE_MODEL","ground_reference_model",ground);
        if(!(localization instanceof EventLocalizationModel))throw new EventDetectionError("UNSUPPORTED_EVENT_LOCALIZATION_MODEL","localization_model",localization);
    }
}

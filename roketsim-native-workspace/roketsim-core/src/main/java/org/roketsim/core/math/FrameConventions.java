package org.roketsim.core.math;

import java.util.List;
import java.util.Map;

/** x_geo bağımsız eksenel koordinattır; BODY z_B ile özdeşleştirilmez. */
public final class FrameConventions {
    private FrameConventions() { }
    public static final Map<ReferenceFrame, List<AxisConvention>> FRAME_AXES = Map.of(
        ReferenceFrame.WORLD_ENU, List.of(new AxisConvention("x_W", "East"),
            new AxisConvention("y_W", "North"), new AxisConvention("z_W", "Up")),
        ReferenceFrame.BODY, List.of(new AxisConvention("x_B", "lateral"),
            new AxisConvention("y_B", "lateral"),
            new AxisConvention("z_B", "longitudinal / thrust / roll axis")));
    public static final Map<String, String> GEOMETRY_AXIAL_CONVENTION = Map.of(
        "origin", "nose tip", "coordinate", "x_geo", "positive_direction", "nose -> tail");
}

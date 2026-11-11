package org.roketsim.core.mass;

/** Five existing structural contributions, each about its own group CG in BODY axes. */
public record StructuralComponentInertias(InertiaTensor3D nose,InertiaTensor3D body,
        InertiaTensor3D fins,InertiaTensor3D motorMount,InertiaTensor3D centeringRings) {
    public StructuralComponentInertias {
        if(nose==null || body==null || fins==null || motorMount==null || centeringRings==null)
            throw new IllegalArgumentException("structural inertia tensors must not be null");
    }
}

package org.roketsim.core.math;

/** Kabul edilmiş Python quaternion.py cebiri; WORLD/BODY yönü ve işlem sırası korunur. */
public final class Quaternions {
    private Quaternions() { }

    /**
     * @param q finite quaternion
     * @return Öklid normu
     */
    public static double norm(Quaternion q) { return Vectors.magnitude(Vector.of(q.toArray())); }

    /**
     * Zero/near-zero normu açık atol ile reddeder.
     * @param q finite quaternion
     * @param atol caller'ın non-negative finite toleransı
     * @return bağımsız normalize değer
     */
    public static Quaternion normalize(Quaternion q, double atol) {
        double length = norm(q);
        requireNonzeroNorm(length, atol);
        return Quaternion.of(new double[] {q.w()/length, q.x()/length, q.y()/length, q.z()/length},
            "normalized quaternion");
    }

    /**
     * @param q finite quaternion
     * @return [w,-x,-y,-z]
     */
    public static Quaternion conjugate(Quaternion q) {
        return new Quaternion(q.w(), -q.x(), -q.y(), -q.z());
    }

    /**
     * Hamilton product soldan sağa uygulanır; normalization yoktur.
     * @param left sol çarpan
     * @param right sağ çarpan
     * @return left * right
     */
    public static Quaternion multiply(Quaternion left, Quaternion right) {
        double w1=left.w(), x1=left.x(), y1=left.y(), z1=left.z();
        double w2=right.w(), x2=right.x(), y2=right.y(), z2=right.z();
        return Quaternion.of(new double[] {
            w1*w2-x1*x2-y1*y2-z1*z2,
            w1*x2+x1*w2+y1*z2-z1*y2,
            w1*y2-x1*z2+y1*w2+z1*x2,
            w1*z2+x1*y2-y1*x2+z1*w2
        }, "Hamilton product");
    }

    /**
     * Non-unit quaternion için de conjugate(q)/norm(q)^2 verir.
     * @param q finite quaternion
     * @param atol açık norm toleransı
     * @return genel inverse
     */
    public static Quaternion inverse(Quaternion q, double atol) {
        double length=norm(q);
        requireNonzeroNorm(length, atol);
        double squared=NumericalValidation.requireFinite(length*length, "quaternion norm squared");
        if (squared==0.0) { throw new IllegalArgumentException("quaternion norm squared must be greater than zero"); }
        Quaternion conjugated=conjugate(q);
        return Quaternion.of(new double[] {conjugated.w()/squared, conjugated.x()/squared,
            conjugated.y()/squared, conjugated.z()/squared}, "quaternion inverse");
    }

    /**
     * Python gibi axis önce normalize edilir, sonra üç boyut denetlenir.
     * @param axis finite eksen
     * @param angleRad radyan right-hand açı
     * @param atol açık axis norm toleransı
     * @return scalar-first yönelim
     */
    public static Quaternion fromAxisAngle(Vector axis, double angleRad, double atol) {
        NumericalValidation.requireFinite(angleRad, "angle_rad");
        Vector unit=Vectors.normalize(axis, atol);
        requireVector3(unit, "axis");
        double half=angleRad/2.0, sinHalf=Math.sin(half);
        return new Quaternion(Math.cos(half),unit.get(0)*sinHalf,unit.get(1)*sinHalf,unit.get(2)*sinHalf);
    }

    /**
     * q_BW*v_B*inverse(q_BW); Python'ın açık orientation normalization'ı uygulanır.
     * @param qBw BODY-to-WORLD quaternion
     * @param vectorBody BODY üç vektörü
     * @param atol açık quaternion norm toleransı
     * @return bağımsız WORLD vektörü
     */
    public static Vector rotateBodyToWorld(Quaternion qBw, Vector vectorBody, double atol) {
        Quaternion orientation=normalize(qBw,atol);
        requireVector3(vectorBody,"vector_b");
        Quaternion pure=new Quaternion(0,vectorBody.get(0),vectorBody.get(1),vectorBody.get(2));
        Quaternion rotated=multiply(multiply(orientation,pure),inverse(orientation,atol));
        return Vector.of(new double[] {rotated.x(),rotated.y(),rotated.z()},3,"vector_w");
    }

    /**
     * inverse(q_BW)*v_W*q_BW; kabul edilmiş ters yön korunur.
     * @param qBw BODY-to-WORLD quaternion
     * @param vectorWorld WORLD üç vektörü
     * @param atol açık quaternion norm toleransı
     * @return bağımsız BODY vektörü
     */
    public static Vector rotateWorldToBody(Quaternion qBw, Vector vectorWorld, double atol) {
        Quaternion orientation=normalize(qBw,atol);
        requireVector3(vectorWorld,"vector_w");
        Quaternion pure=new Quaternion(0,vectorWorld.get(0),vectorWorld.get(1),vectorWorld.get(2));
        Quaternion rotated=multiply(multiply(inverse(orientation,atol),pure),orientation);
        return Vector.of(new double[] {rotated.x(),rotated.y(),rotated.z()},3,"vector_b");
    }

    /**
     * Derived BODY-to-WORLD matrix; her çağrı yeni satır/array'ler üretir.
     * @param q orientation quaternion
     * @param atol açık norm toleransı
     * @return bağımsız 3x3 matrix, authoritative state değildir
     */
    public static double[][] toRotationMatrix(Quaternion q, double atol) {
        Quaternion unit=normalize(q,atol);
        double w=unit.w(),x=unit.x(),y=unit.y(),z=unit.z();
        return new double[][] {
            {1.0-2.0*(y*y+z*z),2.0*(x*y-w*z),2.0*(x*z+w*y)},
            {2.0*(x*y+w*z),1.0-2.0*(x*x+z*z),2.0*(y*z-w*x)},
            {2.0*(x*z-w*y),2.0*(y*z+w*x),1.0-2.0*(x*x+y*y)}
        };
    }

    /**
     * Önce iki girdiyi normalize eder, normalize(delta*old) ile soldan günceller.
     * @param oldOrientation mevcut quaternion
     * @param delta increment quaternion
     * @param atol açık norm toleransı
     * @return bağımsız yeni yönelim
     */
    public static Quaternion applyIncrementLeft(Quaternion oldOrientation, Quaternion delta, double atol) {
        Quaternion oldUnit=normalize(oldOrientation,atol);
        Quaternion deltaUnit=normalize(delta,atol);
        return normalize(multiply(deltaUnit,oldUnit),atol);
    }

    private static void requireNonzeroNorm(double norm, double atol) {
        if (NumericalValidation.isNearZero(norm,atol)) {
            throw new IllegalArgumentException("quaternion norm must be greater than atol; got " + norm);
        }
    }
    private static void requireVector3(Vector vector, String name) {
        if (vector.size()!=3) { throw new IllegalArgumentException(name+" must have shape (3,)"); }
    }
}

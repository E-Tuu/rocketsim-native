package org.roketsim.core.math;

/**
 * Scalar-first immutable quaternion; construction yönelimi normalize etmez.
 * @param w scalar bileşen
 * @param x ilk vektörel bileşen
 * @param y ikinci vektörel bileşen
 * @param z üçüncü vektörel bileşen
 */
public record Quaternion(double w, double x, double y, double z) {
    /**
     * Dört scalar'ı değiştirmeden finite olarak doğrular; zero representation geçerlidir.
     * @param w scalar bileşen
     * @param x ilk vektörel bileşen
     * @param y ikinci vektörel bileşen
     * @param z üçüncü vektörel bileşen
     */
    public Quaternion {
        NumericalValidation.requireFinite(w, "quaternion[0]");
        NumericalValidation.requireFinite(x, "quaternion[1]");
        NumericalValidation.requireFinite(y, "quaternion[2]");
        NumericalValidation.requireFinite(z, "quaternion[3]");
    }

    /**
     * Python as_quaternion girdisini dört bileşenli bağımsız değere çevirir.
     * @param input tek boyutlu sayısal array/List veya quaternion
     * @return finite scalar-first quaternion
     */
    public static Quaternion of(Object input) { return of(input, "quaternion"); }

    /**
     * Shape ve finite doğrulaması J-001 vector sınırını kullanır.
     * @param input tek boyutlu sayısal veri
     * @param name hata alanının adı
     * @return normalize edilmemiş değer
     */
    public static Quaternion of(Object input, String name) {
        Object source = input instanceof Quaternion q ? q.toArray() : input;
        Vector vector = Vector.of(source, 4, name);
        return new Quaternion(vector.get(0), vector.get(1), vector.get(2), vector.get(3));
    }

    /** @return caller'a ait bağımsız [w,x,y,z] array kopyası */
    public double[] toArray() { return new double[] {w, x, y, z}; }
}

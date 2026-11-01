package dev.leo.skinstatues.model;

/** Transformación rígida x → r·x + t. */
public record Transform(Mat3 r, Vec3 t) {
    public static final Transform IDENTITY = new Transform(Mat3.IDENTITY, Vec3.ZERO);

    /** Giro alrededor de un pivote: x → pivote + r·(x − pivote). */
    public static Transform around(Vec3 pivot, Mat3 r) {
        return new Transform(r, pivot.sub(r.mul(pivot)));
    }

    public Vec3 apply(Vec3 v) {
        return r.mul(v).add(t);
    }

    /** this ∘ inner: primero inner, después this. */
    public Transform then(Transform inner) {
        return new Transform(r.mul(inner.r), r.mul(inner.t).add(t));
    }

    public Transform inverse() {
        Mat3 rt = r.transpose();
        return new Transform(rt, rt.mul(t).scale(-1));
    }
}

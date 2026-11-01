package dev.leo.skinstatues.model;

import dev.leo.skinstatues.model.Pose.Part;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometría del jugador en píxeles de skin (plan 021 §1.1): origen entre los pies, y hacia arriba, la cara hacia +z y
 * la derecha del personaje en −x. Cajas, pivotes y UV son los de Minecraft.
 */
final class PlayerModel {
    /** Largo de la mitad de un brazo o pierna (el codo y la rodilla están a la mitad). */
    private static final double HALF = 6;
    /** Cuánto se alarga cada mitad hacia la articulación antes de cortarla con el inglete. */
    private static final double EXTENSION = 12;

    private PlayerModel() {
    }

    static List<Segment> segments(boolean slim, Pose pose, boolean withCape) {
        List<Segment> out = new ArrayList<>();
        Transform body = Transform.around(new Vec3(0, 12, 0), pose.rotation(Part.BODY));

        out.add(box("body", -4, 12, -2, 4, 24, 2, 16, 16, 16, 32, body));
        out.add(box("head", -4, 24, -4, 4, 32, 4, 0, 0, 32, 0,
                body.then(Transform.around(new Vec3(0, 24, 0), pose.rotation(Part.HEAD)))));

        double armW = slim ? 3 : 4;
        limb(out, pose, Part.RIGHT_ARM, -4 - armW, -4, 12, 40, 16, 40, 32, new Vec3(-5, 22, 0), body);
        limb(out, pose, Part.LEFT_ARM, 4, 4 + armW, 12, 32, 48, 48, 48, new Vec3(5, 22, 0), body);
        limb(out, pose, Part.RIGHT_LEG, -4, 0, 0, 0, 16, 0, 32, new Vec3(-2, 12, 0), Transform.IDENTITY);
        limb(out, pose, Part.LEFT_LEG, 0, 4, 0, 16, 48, 0, 48, new Vec3(2, 12, 0), Transform.IDENTITY);

        if (withCape) {
            // La capa de Minecraft es una caja de 10×16×1 girada 180°: su «frente» de la textura mira hacia atrás.
            Transform turn = new Transform(Mat3.rotY(180), Vec3.ZERO);
            Transform t = body.then(Transform.around(new Vec3(0, 24, -2), pose.rotation(Part.CAPE))).then(turn);
            double[] min = {-5, 8, 2}, max = {5, 24, 3};
            out.add(new Segment("cape", min, max, min.clone(), max.clone(), all(), 8, 24, null, null,
                    0, 0, -1, -1, true, false, t));
        }
        return out;
    }

    private static Segment box(String name, double x0, double y0, double z0, double x1, double y1, double z1,
                               int u, int v, int ou, int ov, Transform t) {
        double[] min = {x0, y0, z0}, max = {x1, y1, z1};
        return new Segment(name, min, max, min.clone(), max.clone(), all(), y0, y1, null, null, u, v, ou, ov, false, false, t);
    }

    /**
     * Brazo o pierna en dos mitades. La de abajo gira el doblez alrededor de la articulación; las dos se alargan hacia
     * ella y se cortan con el plano bisector del ángulo (inglete), así el codo queda cerrado sin solaparse (H4 CA2).
     */
    private static void limb(List<Segment> out, Pose pose, Part part, double x0, double x1, double y0,
                             int u, int v, int ou, int ov, Vec3 pivot, Transform parent) {
        double y1 = y0 + 12, z0 = -2, z1 = 2;
        Vec3 joint = new Vec3((x0 + x1) / 2, y1 - HALF, 0);
        Transform limb = parent.then(Transform.around(pivot, pose.rotation(part)));
        Mat3 bend = pose.bend(part);

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 down = bend.mul(new Vec3(0, -1, 0));
        Vec3 n = up.sub(down).normalize();
        double[] tmin = {x0, y0, z0}, tmax = {x1, y1, z1};

        boolean[] upperReal = all();
        upperReal[Segment.NEG_Y] = false;
        out.add(new Segment(part.key() + ".upper", new double[]{x0, joint.y() - EXTENSION, z0}, new double[]{x1, y1, z1},
                tmin, tmax, upperReal, joint.y(), y1, joint, n, u, v, ou, ov, false, false, limb));

        boolean[] lowerReal = all();
        lowerReal[Segment.POS_Y] = false;
        Vec3 nLower = bend.transpose().mul(n).scale(-1);
        out.add(new Segment(part.key() + ".lower", new double[]{x0, y0, z0}, new double[]{x1, joint.y() + EXTENSION, z1},
                tmin, tmax, lowerReal, y0, joint.y(), joint, nLower, u, v, ou, ov, false, !part.arm(),
                limb.then(Transform.around(joint, bend))));
    }

    private static boolean[] all() {
        return new boolean[]{true, true, true, true, true, true};
    }
}

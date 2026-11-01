package dev.leo.skinstatues.model;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pose declarada en poses.yml (plan 021 §2): ángulos en grados por parte. Convenciones (las mismas para el autor):
 * pitch + levanta un brazo o pierna hacia adelante (cabeza: mira abajo; cuerpo y capa: se inclinan hacia adelante y
 * hacia atrás); yaw + gira hacia la izquierda de la estatua; roll + abre un brazo o pierna hacia afuera (cabeza y
 * cuerpo: se ladean a su izquierda); bend es el codo (hacia adelante) o la rodilla (hacia atrás), de 0 a 150.
 */
public record Pose(String name, Map<Part, Angles> parts) {
    public static final Pose STANDING = new Pose("standing", Map.of());

    public enum Part {
        HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, CAPE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean limb() {
            return this == RIGHT_ARM || this == LEFT_ARM || this == RIGHT_LEG || this == LEFT_LEG;
        }

        public boolean arm() {
            return this == RIGHT_ARM || this == LEFT_ARM;
        }

        /** El lado izquierdo de la estatua es +x: ahí «hacia afuera» es el giro contrario. */
        public boolean left() {
            return this == LEFT_ARM || this == LEFT_LEG;
        }

        /** La misma parte del otro lado (la cabeza, el cuerpo y la capa son la misma). */
        public Part opposite() {
            return switch (this) {
                case RIGHT_ARM -> LEFT_ARM;
                case LEFT_ARM -> RIGHT_ARM;
                case RIGHT_LEG -> LEFT_LEG;
                case LEFT_LEG -> RIGHT_LEG;
                default -> this;
            };
        }

        /** Nombre de la clave del doblez de la mitad de abajo, o null si la parte no se dobla. */
        public String bendKey() {
            return arm() ? "elbow" : limb() ? "knee" : null;
        }
    }

    public record Angles(double pitch, double yaw, double roll, double bend) {
        public static final Angles NONE = new Angles(0, 0, 0, 0);
    }

    public Pose {
        parts = parts.isEmpty() ? Map.of() : new EnumMap<>(parts);
    }

    public Angles of(Part p) {
        return parts.getOrDefault(p, Angles.NONE);
    }

    /**
     * La pose volteada de izquierda a derecha (spec 022 H2 CA2): brazos y piernas cambian de lado, todo yaw cambia de
     * signo y también el roll de cabeza, cuerpo y capa; el roll de un miembro ya es «hacia afuera» de su lado.
     */
    public Pose mirrored(String newName) {
        Map<Part, Angles> out = new EnumMap<>(Part.class);
        for (Map.Entry<Part, Angles> e : parts.entrySet()) {
            Part p = e.getKey();
            Angles a = e.getValue();
            out.put(p.opposite(), new Angles(a.pitch(), 0.0 - a.yaw(), p.limb() ? a.roll() : 0.0 - a.roll(), a.bend()));
        }
        return new Pose(newName, out);
    }

    /** Rotación de la parte (sin el doblez) en el orden de Minecraft: primero x, luego y, luego z. */
    public Mat3 rotation(Part p) {
        Angles a = of(p);
        double pitch, roll;
        if (p.limb()) {
            pitch = -a.pitch();                        // colgando hacia −y, pitch + lo lleva hacia +z (adelante)
            roll = p.left() ? a.roll() : -a.roll();    // afuera: +x a la izquierda, −x a la derecha
        } else if (p == Part.CAPE) {
            pitch = a.pitch();                         // la capa cuelga detrás: pitch + la separa de la espalda (−z)
            roll = -a.roll();
        } else {
            pitch = a.pitch();
            roll = -a.roll();
        }
        return Mat3.rotZ(roll).mul(Mat3.rotY(a.yaw())).mul(Mat3.rotX(pitch));
    }

    /** Doblez de la mitad de abajo: el codo lleva el antebrazo hacia adelante y la rodilla la pierna hacia atrás. */
    public Mat3 bend(Part p) {
        double b = of(p).bend();
        return p.arm() ? Mat3.rotX(-b) : Mat3.rotX(b);
    }
}

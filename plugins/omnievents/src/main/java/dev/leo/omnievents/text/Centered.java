package dev.leo.omnievents.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Centrado de líneas en el chat (spec 014, H1): mide el ancho de cada línea con la fuente por defecto de Minecraft y
 * agrega espacios delante. Puro (sin Bukkit).
 */
public final class Centered {
    /** Mitad del ancho del chat por defecto (320 px) menos el margen. */
    static final int CENTER_PX = 154;
    /** Un espacio mide 3 px más 1 de separación. */
    private static final int SPACE_PX = 4;

    private Centered() {
    }

    /** Ancho del glifo sin la separación de 1 px (la fuente por defecto; lo que no está en la tabla, 5). */
    static int glyph(char c) {
        return switch (c) {
            case 'i', 'l', '!', '.', ',', ':', ';', '\'', '|' -> 1;
            case '`' -> 2;
            case 'I', '[', ']', '"', ' ' -> 3;
            case 'f', 'k', 't', '(', ')', '{', '}', '<', '>' -> 4;
            case '@', '~' -> 6;
            default -> 5;
        };
    }

    /** Ancho en píxeles de un texto (cada glifo + 1; la negrita suma 1 más por glifo que no sea espacio). */
    public static int width(String text, boolean bold) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += glyph(c) + 1 + (bold && c != ' ' ? 1 : 0);
        }
        return w;
    }

    /** Espacios que centran una línea de ese ancho (ninguno si no cabe). */
    public static int spaces(int width) {
        int toCompensate = CENTER_PX - width / 2;
        return toCompensate <= 0 ? 0 : toCompensate / SPACE_PX;
    }

    /** Parte el componente en líneas por '\n' y devuelve cada una con los espacios que la centran. */
    public static List<Component> lines(Component message) {
        List<Line> out = new ArrayList<>();
        out.add(new Line());
        walk(message, Style.empty(), out);
        List<Component> result = new ArrayList<>();
        for (Line l : out) {
            Component line = Component.text(" ".repeat(spaces(l.width)));
            for (Component part : l.parts) line = line.append(part);
            result.add(line);
        }
        return result;
    }

    private static final class Line {
        final List<Component> parts = new ArrayList<>();
        int width;
    }

    /** Recorre el árbol con el estilo heredado; cada trozo de texto se copia con el estilo completo (sin hijos). */
    private static void walk(Component c, Style parent, List<Line> out) {
        Style style = c.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        boolean bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        Component self = c.children(List.of()).style(style);
        if (c instanceof TextComponent t) {
            String[] pieces = t.content().split("\n", -1);
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0) out.add(new Line());
                if (pieces[i].isEmpty()) continue;
                Line cur = out.get(out.size() - 1);
                cur.parts.add(((TextComponent) self).content(pieces[i]));
                cur.width += width(pieces[i], bold);
            }
        } else {
            // Otros componentes (traducibles, teclas…): se dejan tal cual con un ancho aproximado.
            Line cur = out.get(out.size() - 1);
            cur.parts.add(self);
            cur.width += 6;
        }
        for (Component child : c.children()) walk(child, style, out);
    }
}

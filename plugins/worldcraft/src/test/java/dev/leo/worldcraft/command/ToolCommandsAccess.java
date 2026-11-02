package dev.leo.worldcraft.command;

import dev.leo.worldcraft.tool.ToolKind;

import java.util.ArrayList;
import java.util.List;

/** Para las pruebas de otro paquete: cómo parte ToolCommands lo escrito tras /wc brush (palabras, opciones, clave=valor). */
public final class ToolCommandsAccess {
    private ToolCommandsAccess() {
    }

    public static List<List<String>> tokens(String[] a, int from, ToolKind kind) {
        ToolCommands.Tokens t = ToolCommands.Tokens.of(a, from, kind);
        List<String> kv = new ArrayList<>();
        t.kv().forEach((k, v) -> kv.add(k + "=" + v));
        return List.of(t.words(), new ArrayList<>(t.flags()), kv);
    }
}

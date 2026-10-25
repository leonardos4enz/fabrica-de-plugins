package dev.leo.omnievents.schema;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;

import java.nio.file.Path;

/** {@code ./gradlew :omnievents:schema}: genera el esquema y la referencia sin servidor (spec 007, H3 CA1). */
public final class SchemaMain {
    private SchemaMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("uso: SchemaMain <carpeta de salida>");
        for (Path p : SchemaFiles.write(Path.of(args[0]), SchemaFiles.render(new Triggers(), new Actions(null)))) {
            System.out.println("Escrito: " + p);
        }
    }
}

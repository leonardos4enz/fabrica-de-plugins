package dev.leo.omnievents.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un paquete: una carpeta de rules/ con pack.yml (spec 011, H1). Lo arma el cargador; después solo se lee. */
public final class Pack {
    /** Una migración: las acciones que corren al subir a esa versión (H3 CA2), compiladas como una regla call. */
    public record Migration(String version, Rule rule) {
    }

    final String name;
    final String folder;
    final String file;
    String version = "1.0.0";
    String description;
    String author;
    boolean enabled = true;
    Map<String, Object> params = Map.of();
    String requiresOmni;
    Map<String, String> requiresPacks = new LinkedHashMap<>();
    List<Migration> migrations = List.of();
    /** Por qué no carga (null si cargó). */
    String error;
    final List<String> rules = new ArrayList<>();
    final List<String> templates = new ArrayList<>();

    Pack(String name, String folder, String file) {
        this.name = name;
        this.folder = folder;
        this.file = file;
    }

    public String name() {
        return name;
    }

    /** Carpeta del paquete, relativa a rules/. */
    public String folder() {
        return folder;
    }

    /** Ruta de su pack.yml, relativa a rules/. */
    public String file() {
        return file;
    }

    public String version() {
        return version;
    }

    public String description() {
        return description;
    }

    public String author() {
        return author;
    }

    /** enabled de pack.yml (sus reglas cargan desactivadas y no corren migraciones). */
    public boolean enabled() {
        return enabled;
    }

    public Map<String, Object> params() {
        return params;
    }

    public String requiresOmni() {
        return requiresOmni;
    }

    public Map<String, String> requiresPacks() {
        return requiresPacks;
    }

    /** En orden de versión. */
    public List<Migration> migrations() {
        return migrations;
    }

    public boolean loaded() {
        return error == null;
    }

    public String error() {
        return error;
    }

    /** Nombres completos de sus reglas cargadas. */
    public List<String> rules() {
        return rules;
    }

    /** Nombres completos de sus plantillas. */
    public List<String> templates() {
        return templates;
    }
}

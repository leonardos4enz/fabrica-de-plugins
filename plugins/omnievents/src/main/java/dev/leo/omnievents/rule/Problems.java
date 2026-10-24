package dev.leo.omnievents.rule;

import java.util.ArrayList;
import java.util.List;

/** Errores y advertencias de configuración, con archivo y ruta (constitución I.3). */
public final class Problems {
    public record Problem(boolean error, String file, String path, String message) {
        @Override
        public String toString() {
            return (error ? "ERROR " : "AVISO ") + file + (path.isEmpty() ? "" : " › " + path) + ": " + message;
        }
    }

    private final List<Problem> list = new ArrayList<>();
    private String file = "";

    public Problems file(String file) {
        this.file = file;
        return this;
    }

    public String file() {
        return file;
    }

    public void error(String path, String message) {
        list.add(new Problem(true, file, path, message));
    }

    public void warn(String path, String message) {
        list.add(new Problem(false, file, path, message));
    }

    public List<Problem> all() {
        return list;
    }

    public long errors() {
        return list.stream().filter(Problem::error).count();
    }

    public long warnings() {
        return list.size() - errors();
    }

    public int size() {
        return list.size();
    }

    /** Marca de posición para saber si hubo errores nuevos desde ahí. */
    public boolean hasErrorsSince(int mark) {
        for (int i = mark; i < list.size(); i++) if (list.get(i).error()) return true;
        return false;
    }
}

package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.region.Box;

/** Lo de cada jugador (o la consola) mientras está conectado: selección, portapapeles y la operación por confirmar. */
public final class Session {
    private String world;
    /** Spec 030: la forma y sus puntos (antes, dos esquinas). */
    private final dev.leo.worldcraft.region.Selector selector = new dev.leo.worldcraft.region.Selector();
    private Clipboard clipboard;
    private String clipboardWorld;
    private boolean show = true;
    Edits.Pending pending;
    /** La última montaña o isla (spec 026 H1 CA4): para /wc reroll, sin volver a leer el suelo. */
    public java.util.function.LongConsumer terrain;
    /** Leyendo el suelo o calculando: el pincel espera (H3 CA5). */
    public boolean terrainBusy;
    /** Spec 029 H4: la máscara global (/wc gmask); solo en memoria, como el portapapeles. null = ninguna. */
    public dev.leo.worldcraft.pattern.Mask gmask;
    /** Spec 034 H6 CA2: cuánto puede calcular una fórmula (ms, /wc timeout); −1 = el de config.yml. */
    public int formulaTimeout = -1;
    /** Spec 036: la copia de seguridad elegida con /wc snapshot (su nombre), o null = la más nueva. */
    public String snapshot;
    /** Spec 040 H3 CA1: dónde ponen las formas y utilidades (player, pos1, pos2, center) y cuánto se corre. */
    public String placement = "player";
    public int[] placementOffset = {0, 0, 0};
    /** Spec 040 H3 CA2: la varita no selecciona (/wc toggleeditwand). */
    public boolean wandOff;
    /** Spec 040 H4 CA5: la zona horaria de las fechas de las copias (/wc tz); null = la del servidor. */
    public java.time.ZoneId zone;
    /** Spec 040 H4 CA5: tras cada obra propia, sus números (/wc trace). */
    public boolean trace;
    /** Spec 041 H3 CA2: la máscara global de origen (/wc gsmask); null = ninguna. */
    public dev.leo.worldcraft.pattern.Mask gsmask;
    /** Spec 041 H3 CA3: la transformación global (/wc gtransform); null = ninguna. */
    public dev.leo.worldcraft.edit.Transforms gtransform;
    /** Spec 041 H3 CA1: los bloques de los patrones por color (/wc gtexture) y lo que dice; null = la tabla entera. */
    public dev.leo.worldcraft.color.BlockColors texture;
    public String textureText;
    /** Spec 041 H4 CA5: saltarse las protecciones (/wc wea). */
    public boolean bypass;
    /** Spec 041 H4 CA8: los esquemas de /wc schem loadall (paste elige uno al azar) y el último archivo cargado. */
    public java.util.List<Clipboard> clipboards = java.util.List.of();
    public java.nio.file.Path loadedFile;

    /** La caja seleccionada o null si falta una esquina o la forma no es una caja (spec 030: ver region()). */
    public Box box() {
        return selector.region() instanceof Box b ? b : null;
    }

    /** Spec 030: la forma seleccionada (cualquiera) o null si falta un punto. */
    public dev.leo.worldcraft.region.Region region() {
        return selector.region();
    }

    public dev.leo.worldcraft.region.Selector selector() {
        return selector;
    }

    public String world() {
        return world;
    }

    public int[] corner1() {
        return selector.corner1();
    }

    public int[] corner2() {
        return selector.corner2();
    }

    /**
     * Un clic (o pos1/pos2) en la forma que se está seleccionando (spec 030 H2); si es de otro mundo, lo de antes se
     * olvida (H1 CA5 de la 025). Devuelve qué quedó («Corner 1», «Center»…).
     */
    public String click(int which, String worldName, int x, int y, int z, int maxPoints) {
        enter(worldName);
        return which == 1 ? selector.primary(x, y, z) : selector.secondary(x, y, z, maxPoints);
    }

    /** Pasa a otro mundo: lo seleccionado en el anterior se olvida. */
    public void enter(String worldName) {
        if (world != null && !world.equals(worldName)) selector.clear();
        world = worldName;
    }

    public void selectBox(String worldName, Box b) {
        world = worldName;
        selector.select(b);
    }

    public void clearSelection() {
        selector.clear();
    }

    public boolean show() {
        return show;
    }

    public void show(boolean v) {
        show = v;
    }

    public Clipboard clipboard() {
        return clipboard;
    }

    public String clipboardWorld() {
        return clipboardWorld;
    }

    public void clipboard(Clipboard c, String worldName) {
        clipboard = c;
        clipboardWorld = worldName;
        // Spec 041: un portapapeles nuevo (copy, load, clearclipboard) deja de lado los de schem loadall.
        clipboards = java.util.List.of();
    }
}

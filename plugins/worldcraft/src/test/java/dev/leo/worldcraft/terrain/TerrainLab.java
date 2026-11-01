package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.TerrainConfig;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.history.JournalSink;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.region.Box;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un laboratorio de terreno para las unitarias y la vista PNG: un mundo plano en memoria (pasto en y = -1, tierra
 * debajo, piedra), la configuración de fábrica y las obras corridas por el motor de la 025 como en el servidor.
 */
public final class TerrainLab {
    public final StateTable table = new StateTable();
    public final FlatWorld world = new FlatWorld(table);
    public final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());
    public final TerrainSettings cfg;
    public final Masks masks;
    public final Issues issues = new Issues();

    public TerrainLab() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(Files.readString(Path.of("src/main/resources/config.yml")));
            YamlConfiguration terrain = new YamlConfiguration();
            terrain.loadFromString(Files.readString(Path.of("src/main/resources/terrain.yml")));
            cfg = TerrainConfig.load(config.getConfigurationSection("terrain"), terrain, parser, issues);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        masks = new Masks(cfg.ground().compile(table), cfg.cover().compile(table), cfg.buryable().compile(table),
                id -> BlockRegistry.AIR.contains(table.material(id)));
    }

    /** Mundo plano: pasto en y = -1, tierra de -4 a -2, piedra debajo, aire arriba. */
    public static final class FlatWorld extends MemoryWorld {
        public final int grass, dirt;

        FlatWorld(StateTable t) {
            super(t);
            grass = t.intern("minecraft:grass_block");
            dirt = t.intern("minecraft:dirt");
        }

        @Override
        public int get(int x, int y, int z) {
            Integer s = blocks.get(key(x, y, z));
            if (s != null) return s;
            return y >= 0 ? air : y == -1 ? grass : y >= -4 ? dirt : stone;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }
    }

    /** Corre una fuente hasta el final por el motor (con diario si se da un archivo). */
    public EditJob run(EditSource src, EditJob.Mode mode, EditJob.Capture capture, Path journal) {
        EditJob.Sink sink = journal == null ? new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        } : new JournalSink(journal, table, Runnable::run, e -> {
            throw new IllegalStateException(e);
        });
        EditJob j = new EditJob(1, new UUID(1, 1), "test", world, src, mode, sink, capture, Long.MAX_VALUE, System::nanoTime);
        for (int i = 0; i < 10_000_000 && !j.step(Long.MAX_VALUE, 41); i++) {
            // sigue
        }
        return j;
    }

    /** Lo que mostraría la fuente como vista previa (posición → estado), sin tocar el mundo. */
    public Map<Long, Integer> preview(EditSource src) {
        Map<Long, Integer> out = new java.util.HashMap<>();
        EditJob j = new EditJob(1, new UUID(1, 1), "preview", world, src, EditJob.Mode.PREVIEW,
                (x, y, z, b, a, tb, ta) -> out.put(IslandShape.key(x, y, z), a), null, Long.MAX_VALUE, System::nanoTime);
        for (int i = 0; i < 10_000_000 && !j.step(Long.MAX_VALUE, 41); i++) {
            // sigue
        }
        return out;
    }

    /** Lee el suelo como lo hace el servidor. */
    public HeightField scan(Box rect, int top, int bottom) {
        ColumnScan scan = new ColumnScan(rect, top, bottom, masks.ground(), masks.cover(), masks.buryable(), masks.air());
        run(scan, EditJob.Mode.READ, scan, null);
        return scan.field();
    }

    public Options.Resolved options(Options.Tool tool, String... words) {
        List<String> plain = new ArrayList<>();
        Map<String, String> kv = new java.util.LinkedHashMap<>();
        for (String w : words) {
            int eq = w.indexOf('=');
            if (eq > 0) kv.put(w.substring(0, eq), w.substring(eq + 1));
            else plain.add(w);
        }
        return Options.resolve(tool, plain, kv, cfg, cfg.maxRadius(), 1234);
    }

    public static MountainShape mountain(Options.Resolved o) {
        return new MountainShape(o.integer("radius"), o.integer("height"), o.integer("peaks"), o.number("roughness"),
                o.number("ridges"), o.number("sharpness"), o.number("crater"), o.seed());
    }

    public static IslandShape island(Options.Resolved o) {
        return new IslandShape(o.integer("radius"), o.number("depth"), o.number("relief"), o.number("spikes"),
                o.number("roots"), o.integer("trees"), o.seed());
    }

    /** Lo que hace /wc mountain: leer el suelo, calcular y la fuente (piel o completa). */
    public record Mountain(HeightField field, MountainShape.Plan plan, Options.Resolved options) {
    }

    public Mountain planMountain(int cx, int cz, int groundY, String... words) {
        Options.Resolved o = options(Options.Tool.MOUNTAIN, words);
        int r = o.integer("radius"), h = o.integer("height");
        HeightField f = scan(new Box(cx - r, 0, cz - r, cx + r, 0, cz + r), groundY + h + 2, groundY - 48);
        return new Mountain(f, mountain(o).plan(f, cx, cz, groundY, world.maxY()), o);
    }

    public HeightSource mountainSource(Mountain m, boolean skin) {
        return new HeightSource(m.field, m.plan.newTop(), HeightSource.Mode.GENERATE,
                m.options.style().painter(table, m.options.seed()), m.plan.slope(), m.plan.base(), m.options.integer("height"),
                masks, 1, world.air, skin);
    }

    public IslandSource islandSource(IslandShape.Plan plan, Options.Resolved o, boolean skin) {
        return new IslandSource(plan, o.style().painter(table, o.seed()), masks, skin);
    }

    public int state(String s) {
        try {
            return parser.pattern(s).compile(table).apply(0, 0, 0, 0);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

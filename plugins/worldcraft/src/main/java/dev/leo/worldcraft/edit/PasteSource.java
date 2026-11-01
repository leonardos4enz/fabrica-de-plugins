package dev.leo.blockwright.edit;

import dev.leo.blockwright.block.StateOps;
import dev.leo.blockwright.region.Box;
import dev.leo.blockwright.region.ChunkOrder;
import dev.leo.blockwright.region.Shape;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Pegar el portapapeles (H3 CA2, CA3): recorre la caja de destino en orden de chunks y busca cada posición en el
 * portapapeles con la transformación inversa. Por defecto el aire copiado no se pega (no borra lo que hay alrededor);
 * con «--air» sí.
 */
public final class PasteSource implements EditSource {
    private final Clipboard clip;
    private final int px, py, pz;
    private final boolean withAir;
    private final IntPredicate isAir;
    private final StateOps ops;
    private final Box box;
    private final ChunkOrder order;
    private final Map<Integer, Integer> turned = new HashMap<>();
    private final int[] v = new int[3];
    private byte[] tile;

    public PasteSource(Clipboard clip, int px, int py, int pz, boolean withAir, IntPredicate isAir, StateOps ops) {
        this.clip = clip;
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.withAir = withAir;
        this.isAir = isAir;
        this.ops = ops;
        this.box = clip.target(px, py, pz);
        this.order = new ChunkOrder(box, Shape.FILL);
    }

    @Override
    public boolean next(int[] p) {
        return order.next(p);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        tile = null;
        clip.transform().inverse(x - px, y - py, z - pz, v);
        int s = clip.at(v[0], v[1], v[2]);
        if (s < 0 || (!withAir && isAir.test(s))) return KEEP;
        tile = clip.tileAt(v[0], v[1], v[2]);
        return turned.computeIfAbsent(s, this::turn);
    }

    private int turn(int s) {
        for (Transform.Step step : clip.transform().steps()) {
            s = switch (step) {
                case ROTATE_90 -> ops.rotate90(s);
                case FLIP_X -> ops.flip(s, 'x');
                case FLIP_Y -> ops.flip(s, 'y');
                case FLIP_Z -> ops.flip(s, 'z');
            };
        }
        return s;
    }

    @Override
    public byte[] tile() {
        return tile;
    }

    @Override
    public long volume() {
        return box.volume();
    }

    @Override
    public Box bounds() {
        return box;
    }
}

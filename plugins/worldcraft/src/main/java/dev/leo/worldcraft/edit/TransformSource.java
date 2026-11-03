package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.tool.BrushMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Spec 041 H3 CA3: una obra con sus bloques transformados (//gtransform, /transform). Recorre la fuente de adentro y por
 * cada posición da la (o las, con scale) transformadas; qué poner lo sigue decidiendo la de adentro, con su posición de
 * origen y el bloque que hay donde cae. Las posiciones no salen en orden de chunks: el trabajo espera cada chunk.
 */
public final class TransformSource implements EditSource {
    private final EditSource inner;
    private final Transforms t;
    private final long seed;
    /** La caja antes de cada paso (para girar y escalar alrededor de su centro). */
    private final Box[] boxes;
    private final Box bounds;
    private final List<int[]> queue = new ArrayList<>();
    private final int[] src = new int[3], q = new int[3];
    private int qi;

    public TransformSource(EditSource inner, Transforms t, long seed) {
        this.inner = inner;
        this.t = t;
        this.seed = seed;
        List<Transforms.Step> steps = t.steps();
        boxes = new Box[steps.size()];
        Box cur = inner.bounds();
        for (int i = 0; i < steps.size(); i++) {
            boxes[i] = cur;
            cur = Transforms.single(steps.get(i)).bounds(cur);
        }
        bounds = cur;
    }

    @Override
    public boolean next(int[] p) {
        while (qi >= queue.size()) {
            queue.clear();
            qi = 0;
            if (!inner.next(q)) return false;
            src[0] = q[0];
            src[1] = q[1];
            src[2] = q[2];
            expand(q[0], q[1], q[2]);
        }
        int[] r = queue.get(qi++);
        p[0] = r[0];
        p[1] = r[1];
        p[2] = r[2];
        return true;
    }

    private void expand(int x, int y, int z) {
        List<int[]> cur = new ArrayList<>();
        cur.add(new int[]{x, y, z});
        List<Transforms.Step> steps = t.steps();
        for (int i = 0; i < steps.size(); i++) {
            Transforms.Step s = steps.get(i);
            Box b = boxes[i];
            List<int[]> next = new ArrayList<>(cur.size());
            for (int[] v : cur) {
                switch (s.name()) {
                    case "offset" -> next.add(new int[]{v[0] + s.args()[0], v[1] + s.args()[1], v[2] + s.args()[2]});
                    case "rotate" -> {
                        int[] o = new int[3];
                        Transforms.rotate(s.args()[0], b, v[0], v[1], v[2], o);
                        next.add(o);
                    }
                    case "flip" -> {
                        int[] o = v.clone();
                        int axis = s.args()[0];
                        int lo = axis == 0 ? b.minX() : axis == 1 ? b.minY() : b.minZ(), hi = axis == 0 ? b.maxX() : axis == 1 ? b.maxY() : b.maxZ();
                        o[axis] = lo + hi - v[axis];
                        next.add(o);
                    }
                    case "spread" -> {
                        int n = s.args()[0], w = 2 * n + 1;
                        // El salto depende solo de la posición de origen y la semilla de la obra (la vista previa da lo mismo).
                        next.add(new int[]{v[0] + (int) (BrushMath.hash01(v[0], v[1], v[2], seed) * w) - n,
                                v[1] + (int) (BrushMath.hash01(v[0], v[1], v[2], seed ^ 0x5bd1e995L) * w) - n,
                                v[2] + (int) (BrushMath.hash01(v[0], v[1], v[2], seed ^ 0x27d4eb2dL) * w) - n});
                    }
                    default -> {
                        int n = s.args()[0];
                        int cx = Math.floorDiv(b.minX() + b.maxX(), 2), cy = Math.floorDiv(b.minY() + b.maxY(), 2), cz = Math.floorDiv(b.minZ() + b.maxZ(), 2);
                        int bx = cx + (v[0] - cx) * n, by = cy + (v[1] - cy) * n, bz = cz + (v[2] - cz) * n;
                        for (int dy = 0; dy < n; dy++)
                            for (int dz = 0; dz < n; dz++)
                                for (int dx = 0; dx < n; dx++) next.add(new int[]{bx + dx, by + dy, bz + dz});
                    }
                }
            }
            cur = next;
        }
        queue.addAll(cur);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return inner.target(src[0], src[1], src[2], current);
    }

    @Override
    public byte[] tile() {
        return inner.tile();
    }

    @Override
    public long volume() {
        return inner.volume() * t.factor();
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public boolean ready() {
        return inner.ready();
    }

    @Override
    public long skipped() {
        return inner.skipped();
    }
}

package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.PasteSource;
import dev.leo.worldcraft.entity.EntityStage;
import dev.leo.worldcraft.region.Box;
import org.bukkit.World;

import java.util.List;
import java.util.Set;

/** La obra de /wc paste (bloques, datos y entidades del portapapeles); spec 037: también la usa la API. */
public final class PasteOp {
    private PasteOp() {
    }

    /**
     * @param o        dónde va el origen del portapapeles
     * @param entities pegar las entidades del portapapeles (spec 031)
     * @param clear    quitar antes las entidades de donde se pega (--remove-entities)
     */
    public static Edits.Op of(Worldcraft plugin, World world, Clipboard clip, int[] o, Box target, boolean air, boolean entities, boolean clear) {
        PaperBlocks blocks = plugin.edits().blocks();
        Edits.Op op = new Edits.Op("paste", clip.size() + (air ? " with air" : ""), world, target, clip.volume(),
                () -> new PasteSource(clip, o[0], o[1], o[2], air, blocks::isAir, blocks), clip.distinct(), null);
        // Spec 031 H1 CA3–CA4: las entidades del portapapeles van después de los bloques; --remove-entities quita antes las de ahí.
        List<dev.leo.worldcraft.entity.EntityCopy> ents = entities ? clip.entitiesAt(o[0], o[1], o[2]) : List.of();
        if (ents.isEmpty() && !clear) return op;
        var msg = plugin.messages();
        List<EntityStage.Item> items = ents.stream().map(e -> new EntityStage.Item(null, e)).toList();
        String note = (ents.isEmpty() ? "" : msg.text("note-entities-add", "count", Edits.fmt(ents.size())))
                + (clear ? (ents.isEmpty() ? "" : " ") + msg.text("note-entities-clear") : "");
        return op.with(new Edits.EntityWork(
                clear ? (w, sink, max, b) -> new EntityStage.Scan(w, target, e -> true, true, max, sink) : null,
                ents.isEmpty() ? null : (w, sink, max, b) -> new EntityStage.Spawn(w, () -> items, sink),
                ents.size(), ents.isEmpty() ? Set.of() : clip.entityTypes(), note));
    }
}

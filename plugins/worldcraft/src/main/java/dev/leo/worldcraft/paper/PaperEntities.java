package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.entity.EntityCopy;
import dev.leo.worldcraft.entity.EntityInfo;
import dev.leo.worldcraft.entity.EntityWorld;
import dev.leo.worldcraft.entity.Kind;
import io.papermc.paper.entity.EntitySerializationFlag;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Las entidades de un mundo de Paper para el motor (plan 031 §1.1): se copian con {@code serializeEntity} (NBT binario
 * con lo montado) y se recrean con {@code deserializeEntity} + {@code spawnAt}, sin NMS. Hilo principal.
 */
@SuppressWarnings("deprecation") // UnsafeValues#serializeEntity: la única forma sin NMS de copiar una entidad entera
public final class PaperEntities implements EntityWorld {
    private final dev.leo.worldcraft.Worldcraft plugin;
    private final World world;
    private final Set<Long> requested = new HashSet<>();

    public PaperEntities(dev.leo.worldcraft.Worldcraft plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public boolean ready(int cx, int cz) {
        if (world.isChunkLoaded(cx, cz)) {
            Chunk c = world.getChunkAt(cx, cz);
            if (c.isEntitiesLoaded()) return true;
        }
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        if (requested.add(key)) world.getChunkAtAsync(cx, cz, true);
        return false;
    }

    @Override
    public List<EntityInfo> list(int cx, int cz) {
        List<EntityInfo> out = new ArrayList<>();
        for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
            if (e.getVehicle() != null || !e.isValid()) continue;
            Location l = e.getLocation();
            out.add(new EntityInfo(e.getUniqueId(), e.getType().getKey().toString(), l.getX(), l.getY(), l.getZ(), kinds(e)));
        }
        return out;
    }

    /** Lo que es para butcher y remove (spec 031 H4, H5). */
    static Set<Kind> kinds(Entity e) {
        Set<Kind> k = EnumSet.noneOf(Kind.class);
        if (e instanceof Player) k.add(Kind.PLAYER);
        if (e instanceof LivingEntity) k.add(Kind.LIVING);
        if (e instanceof Enemy) k.add(Kind.MONSTER);
        if (e instanceof Tameable t && t.isTamed()) k.add(Kind.PET);
        // Aldeanos y comerciantes (NPC de Bukkit) y los NPC de Citizens (marcados con la metadata «NPC»).
        if (e instanceof NPC || e.hasMetadata("NPC")) k.add(Kind.NPC);
        if (e instanceof IronGolem || e instanceof Snowman) k.add(Kind.GOLEM);
        if (e instanceof Animals) k.add(Kind.ANIMAL);
        if (e instanceof Ambient) k.add(Kind.AMBIENT);
        if (e instanceof WaterMob) k.add(Kind.WATER);
        if (e.customName() != null) k.add(Kind.NAMED);
        if (e instanceof ArmorStand) k.add(Kind.ARMOR_STAND);
        if (e instanceof Item) k.add(Kind.ITEM);
        if (e instanceof Projectile) k.add(Kind.PROJECTILE);
        if (e instanceof AbstractArrow) k.add(Kind.ARROW);
        if (e instanceof ExperienceOrb) k.add(Kind.XP);
        if (e instanceof Boat) k.add(Kind.BOAT);
        if (e instanceof Minecart) k.add(Kind.MINECART);
        if (e instanceof TNTPrimed) k.add(Kind.TNT);
        if (e instanceof FallingBlock) k.add(Kind.FALLING);
        if (e instanceof Painting) k.add(Kind.PAINTING);
        if (e instanceof ItemFrame) k.add(Kind.ITEM_FRAME);
        return k;
    }

    @Override
    public EntityCopy copy(UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e == null || !e.isValid() || e instanceof Player) return null;
        try {
            byte[] nbt = Bukkit.getUnsafe().serializeEntity(e, EntitySerializationFlag.PASSENGERS);
            Location l = e.getLocation();
            List<String> contains = new ArrayList<>();
            types(e, contains, 0);
            return new EntityCopy(e.getType().getKey().toString(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), nbt, contains);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Could not copy entity " + e.getType(), ex);
            return null;
        }
    }

    private static void types(Entity e, List<String> out, int depth) {
        out.add(e.getType().getKey().toString());
        if (e instanceof FallingBlock f) out.add("block:" + f.getBlockData().getMaterial().getKey());
        if (depth < 16) for (Entity p : e.getPassengers()) types(p, out, depth + 1);
    }

    @Override
    public String type(UUID id) {
        Entity e = Bukkit.getEntity(id);
        return e == null || !e.isValid() || !e.getWorld().equals(world) ? null : e.getType().getKey().toString();
    }

    @Override
    public boolean remove(UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e == null || !e.isValid() || e instanceof Player) return false;
        removeWithPassengers(e, 0);
        return true;
    }

    private static void removeWithPassengers(Entity e, int depth) {
        if (depth < 16) for (Entity p : new ArrayList<>(e.getPassengers())) if (!(p instanceof Player)) removeWithPassengers(p, depth + 1);
        e.remove();
    }

    @Override
    public UUID spawn(EntityCopy c) {
        try {
            Entity e = Bukkit.getUnsafe().deserializeEntity(c.nbt(), world, false, true);
            Location l = new Location(world, c.x(), c.y(), c.z(), c.yaw(), c.pitch());
            if (!e.spawnAt(l)) return null;
            if (e instanceof Hanging h && !c.turn().isIdentity()) {
                BlockFace f = h.getFacing();
                int[] v = c.face(f.getModX(), f.getModY(), f.getModZ());
                BlockFace nf = face(v);
                if (nf != null && nf != f) h.setFacingDirection(nf, true);
            }
            return e.getUniqueId();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not create a " + c.type().replace("minecraft:", "")
                    + " at " + (int) Math.floor(c.x()) + " " + (int) Math.floor(c.y()) + " " + (int) Math.floor(c.z()) + ": " + ex.getMessage());
            return null;
        }
    }

    private static BlockFace face(int[] v) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            if (f.getModX() == v[0] && f.getModY() == v[1] && f.getModZ() == v[2]) return f;
        }
        return null;
    }
}

package dev.leo.worldcraft.entity;

import dev.leo.worldcraft.edit.Transform;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 031: girar entidades, qué quita butcher y qué entiende remove. */
class EntityTest {
    static EntityCopy at(double x, double y, double z, float yaw, float pitch) {
        return new EntityCopy("minecraft:armor_stand", x, y, z, yaw, pitch, new byte[0], List.of("minecraft:armor_stand"));
    }

    @Test
    void H1_CA3_girar_90_mueve_la_posicion_como_su_bloque_y_la_mirada() {
        // En el bloque (0, 0, -3), mirando al norte (yaw 180): girada 90° queda en el bloque (3, 0, 0) mirando al este.
        EntityCopy e = at(0.5, 0, -2.5, 180, 10).transformed(Transform.IDENTITY.rotate90());
        assertEquals(3.5, e.x(), 1e-9);
        assertEquals(0, e.y(), 1e-9);
        assertEquals(0.5, e.z(), 1e-9);
        assertEquals(-90, e.yaw(), 1e-3);
        assertEquals(10, e.pitch(), 1e-6, "girar no cambia la inclinación");
        assertArrayEquals(new int[]{1, 0, 0}, e.face(0, 0, -1), "la pared norte de un marco pasa a ser la este");
    }

    @Test
    void H1_CA3_cuatro_giros_vuelven_al_principio_y_un_punto_dentro_del_bloque_sigue_dentro() {
        EntityCopy e0 = at(2.25, 64.1, -7.9, 33, -20);
        EntityCopy e = e0;
        for (int i = 0; i < 4; i++) e = e.transformed(Transform.IDENTITY.rotate90());
        assertEquals(e0.x(), e.x(), 1e-9);
        assertEquals(e0.z(), e.z(), 1e-9);
        assertEquals(33, e.yaw(), 1e-3);
        assertArrayEquals(new int[]{0, 0, -1}, e.face(0, 0, -1));
        // El bloque de la entidad (2, 64, -8) girado una vez es (8, 64, 2): la entidad cae dentro de ese bloque.
        EntityCopy r = e0.transformed(Transform.IDENTITY.rotate90());
        assertEquals(8, (int) Math.floor(r.x()));
        assertEquals(2, (int) Math.floor(r.z()));
    }

    @Test
    void H1_CA3_voltear_espeja_la_posicion_la_mirada_y_en_y_la_inclinacion() {
        EntityCopy fx = at(1.5, 0, 0.5, -90, 0).transformed(Transform.IDENTITY.flip('x')); // mirando al este
        assertEquals(-0.5, fx.x(), 1e-9);
        assertEquals(90, fx.yaw(), 1e-3, "ahora mira al oeste");
        EntityCopy fy = at(0.5, 2.2, 0.5, 0, 30).transformed(Transform.IDENTITY.flip('y'));
        assertEquals(-1.2, fy.y(), 1e-9);
        assertEquals(-30, fy.pitch(), 1e-6);
        assertArrayEquals(new int[]{0, -1, 0}, fy.face(0, 1, 0));
    }

    @Test
    void H4_CA2_butcher_de_fabrica_solo_hostiles_y_cada_opcion_agrega_lo_suyo() {
        Butcher def = Butcher.of(Set.of());
        Set<Kind> zombie = EnumSet.of(Kind.LIVING, Kind.MONSTER);
        Set<Kind> cow = EnumSet.of(Kind.LIVING, Kind.ANIMAL);
        Set<Kind> dog = EnumSet.of(Kind.LIVING, Kind.ANIMAL, Kind.PET);
        Set<Kind> villager = EnumSet.of(Kind.LIVING, Kind.NPC);
        Set<Kind> namedZombie = EnumSet.of(Kind.LIVING, Kind.MONSTER, Kind.NAMED);
        Set<Kind> golem = EnumSet.of(Kind.LIVING, Kind.GOLEM);
        Set<Kind> bat = EnumSet.of(Kind.LIVING, Kind.AMBIENT);
        Set<Kind> cod = EnumSet.of(Kind.LIVING, Kind.WATER);
        Set<Kind> stand = EnumSet.of(Kind.LIVING, Kind.ARMOR_STAND);
        Set<Kind> allay = EnumSet.of(Kind.LIVING);
        Set<Kind> player = EnumSet.of(Kind.LIVING, Kind.PLAYER);
        Set<Kind> item = EnumSet.of(Kind.ITEM);
        assertTrue(def.kills(zombie));
        for (Set<Kind> k : List.of(cow, dog, villager, namedZombie, golem, bat, cod, stand, allay, player, item)) {
            assertFalse(def.kills(k), k.toString());
        }
        assertTrue(Butcher.of(Set.of("animals")).kills(cow));
        assertFalse(Butcher.of(Set.of("animals")).kills(dog), "una mascota necesita --pets");
        assertTrue(Butcher.of(Set.of("pets")).kills(dog));
        assertTrue(Butcher.of(Set.of("npcs")).kills(villager));
        assertTrue(Butcher.of(Set.of("named")).kills(namedZombie));
        assertTrue(Butcher.of(Set.of("golems")).kills(golem));
        assertTrue(Butcher.of(Set.of("ambient")).kills(bat));
        assertTrue(Butcher.of(Set.of("water")).kills(cod));
        assertTrue(Butcher.of(Set.of("armorstands")).kills(stand));
        Butcher friendly = Butcher.of(Set.of("friendly"));
        for (Set<Kind> k : List.of(cow, dog, villager, namedZombie, golem, bat, allay)) assertTrue(friendly.kills(k), k.toString());
        assertFalse(friendly.kills(cod), "--friendly no incluye los acuáticos (como -f de WorldEdit)");
        assertFalse(friendly.kills(stand));
        assertFalse(Butcher.of(Set.of("friendly", "water", "armorstands", "pets")).kills(player), "nunca jugadores");
    }

    @Test
    void H5_CA1_remove_entiende_los_grupos_de_worldedit_y_los_tipos() {
        List<String> types = List.of("minecraft:cow", "minecraft:arrow", "minecraft:item", "minecraft:player");
        RemoveType items = RemoveType.parse("items", types);
        assertTrue(items.matches(new EntityInfo(null, "minecraft:item", 0, 0, 0, Set.of(Kind.ITEM))));
        assertFalse(items.matches(new EntityInfo(null, "minecraft:cow", 0, 0, 0, Set.of(Kind.LIVING))));
        RemoveType cow = RemoveType.parse("Minecraft:Cow", types);
        assertEquals("cow", cow.name());
        assertTrue(cow.matches(new EntityInfo(null, "minecraft:cow", 0, 0, 0, Set.of(Kind.LIVING))));
        assertTrue(RemoveType.parse("arrows", types).matches(new EntityInfo(null, "minecraft:arrow", 0, 0, 0, Set.of(Kind.ARROW, Kind.PROJECTILE))));
        assertNull(RemoveType.parse("player", types), "nunca jugadores");
        assertNull(RemoveType.parse("cows", types));
        assertTrue(RemoveType.words(types).containsAll(List.of("items", "cow", "arrow")));
        assertFalse(RemoveType.words(types).contains("player"));
    }
}

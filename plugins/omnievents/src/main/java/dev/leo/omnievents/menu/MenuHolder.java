package dev.leo.omnievents.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/**
 * El dueño del inventario de un menú abierto (spec 013, plan §1.3): así se reconoce un menú sin comparar títulos (un
 * cofre renombrado con el mismo título no lo es). Guarda qué ítem se ve en cada casilla.
 */
public final class MenuHolder implements InventoryHolder {
    final Menu menu;
    final UUID player;
    final Map<String, Object> args;
    Inventory inventory;
    Map<Integer, Menu.Item> shown = Map.of();
    /** true: se cierra sin on_close (recarga). */
    boolean silent;

    MenuHolder(Menu menu, UUID player, Map<String, Object> args) {
        this.menu = menu;
        this.player = player;
        this.args = args;
    }

    public Menu menu() {
        return menu;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

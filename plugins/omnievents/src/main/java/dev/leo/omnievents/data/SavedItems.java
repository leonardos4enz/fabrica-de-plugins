package dev.leo.omnievents.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/** Ítems guardados con /omni item save para usarlos como saved:nombre. */
public final class SavedItems {
    private final File file;
    private final YamlConfiguration yaml;

    public SavedItems(File dataFolder) {
        this.file = new File(dataFolder, "items.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public ItemStack get(String name) {
        return yaml.getItemStack(name.toLowerCase());
    }

    public void save(String name, ItemStack item) throws IOException {
        yaml.set(name.toLowerCase(), item.clone());
        yaml.save(file);
    }

    public boolean delete(String name) throws IOException {
        if (!yaml.contains(name.toLowerCase())) return false;
        yaml.set(name.toLowerCase(), null);
        yaml.save(file);
        return true;
    }

    public Set<String> names() {
        return new TreeSet<>(yaml.getKeys(false));
    }
}

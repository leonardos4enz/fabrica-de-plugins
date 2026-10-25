package dev.leo.omnievents.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/** Solo se instancia si WorldGuard está instalado (constitución VI.2). */
public final class WorldGuardRegions implements Regions {
    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<Region> at(Location l) {
        if (l == null || l.getWorld() == null) return List.of();
        ApplicableRegionSet set = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(l));
        List<Region> out = new ArrayList<>();
        for (ProtectedRegion r : set) {
            if (r.getId().equals(ProtectedRegion.GLOBAL_REGION)) continue;
            out.add(new Region(r.getId(), r.getPriority(), l.getWorld().getName()));
        }
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return out;
    }
}

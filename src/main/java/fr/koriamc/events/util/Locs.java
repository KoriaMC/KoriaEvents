package fr.koriamc.events.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Positions écrites "monde,x,y,z" dans la configuration.
 */
public final class Locs {

    private Locs() {
    }

    /** @return la position, ou null si le texte est vide, invalide ou si le monde n'existe pas */
    public static Location parse(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(p[0].trim());
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()),
                    Double.parseDouble(p[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Position du bloc : "monde,x,y,z". */
    public static String format(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public static String pretty(Location l) {
        return String.format(Locale.ROOT, "%d, %d, %d (%s)", l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                l.getWorld().getName());
    }

    /** Centre du bloc. */
    public static Location center(Location l) {
        return new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5);
    }
}

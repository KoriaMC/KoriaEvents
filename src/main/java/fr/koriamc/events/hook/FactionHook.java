package fr.koriamc.events.hook;

import fr.koriamc.factions.api.KoriaFactionsAPI;
import fr.koriamc.factions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seule classe qui interroge KoriaFactions : faction d'un joueur, points, Warzone.
 */
public final class FactionHook {

    /** Identifiant de la faction du joueur, ou null. */
    public String getFactionId(Player player) {
        Faction f = KoriaFactionsAPI.getFaction(player);
        return f == null || !f.isNormal() ? null : f.getId();
    }

    public String getTag(String id) {
        Faction f = id == null ? null : KoriaFactionsAPI.getFactionById(id);
        return f == null ? "?" : f.getTag();
    }

    /** Ajoute des points de classement à une faction. @return le gain appliqué */
    public long addPoints(String id, long points, String reason) {
        Faction f = id == null ? null : KoriaFactionsAPI.getFactionById(id);
        if (f == null || !f.isNormal() || points <= 0) {
            return 0;
        }
        return KoriaFactionsAPI.addPoints(f, points, reason);
    }

    public boolean isWarZone(Location location) {
        return location != null && KoriaFactionsAPI.isInWarZone(location);
    }

    /** Membres connectés d'une faction. */
    public List<Player> online(String id) {
        List<Player> out = new ArrayList<Player>();
        Faction f = id == null ? null : KoriaFactionsAPI.getFactionById(id);
        if (f == null) {
            return out;
        }
        for (UUID uuid : f.getMemberIds()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }
}

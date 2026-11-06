package fr.koriamc.events.warzone;

import fr.koriamc.events.KoriaEvents;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Règles de la Warzone (territoire WARZONE de KoriaFactions) : délai entre deux perles de
 * l'Ender et aucune téléportation vers la zone. Les explosions y sont interdites par
 * KoriaFactions (territoire.json : explosions.warzone).
 */
public final class WarzoneListener implements Listener {

    private final KoriaEvents plugin;
    private final Map<UUID, Long> lastPearl = new HashMap<UUID, Long>();

    private boolean enabled;
    private int pearlSeconds;
    private boolean noTeleportIn;

    public WarzoneListener(KoriaEvents plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        enabled = c != null && c.getBoolean("actif", true);
        pearlSeconds = c == null ? 30 : Math.max(0, c.getInt("cooldown-perles-secondes", 30));
        noTeleportIn = c == null || !c.getBoolean("teleportation-entrante", false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPearl(PlayerInteractEvent event) {
        if (!enabled || pearlSeconds <= 0 || event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.factions().isWarZone(player.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastPearl.get(player.getUniqueId());
        if (last != null && now - last < pearlSeconds * 1000L) {
            event.setCancelled(true);
            player.updateInventory();
            plugin.msg().send(player, "warzone.perle-cooldown", "secondes", (pearlSeconds * 1000L - (now - last)) / 1000 + 1);
            return;
        }
        lastPearl.put(player.getUniqueId(), now);
    }

    /** Aucune téléportation vers la Warzone : il faut y aller à pied (les perles restent permises). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled || !noTeleportIn || event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getTo() == null || event.getPlayer().hasPermission("koriaevents.warzone.bypass")) {
            return;
        }
        if (plugin.factions().isWarZone(event.getTo()) && !plugin.factions().isWarZone(event.getFrom())) {
            event.setCancelled(true);
            plugin.msg().send(event.getPlayer(), "warzone.teleportation-interdite");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPearl.remove(event.getPlayer().getUniqueId());
    }
}

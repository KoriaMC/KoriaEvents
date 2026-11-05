package fr.koriamc.events.game;

import fr.koriamc.events.KoriaEvents;
import fr.koriamc.events.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Event Farm : une ressource tirée au hasard, 20 minutes pour en récolter le plus possible
 * en faction. Points de classement au top 3.
 */
@SuppressWarnings("deprecation")
public final class FarmEvent extends GameEvent {

    private final Random random = new Random();
    private Material resource;
    private String resourceName;
    private int matureData;

    public FarmEvent(KoriaEvents plugin) {
        super(plugin, "farm");
    }

    @Override
    public String questKey() {
        return "FARM";
    }

    @Override
    protected String onStart() {
        ConfigurationSection list = config.getConfigurationSection("ressources");
        List<String> keys = new ArrayList<String>();
        if (list != null) {
            for (String key : list.getKeys(false)) {
                if (Material.matchMaterial(key.toUpperCase(Locale.ROOT)) != null) {
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            return "farm-sans-ressource";
        }
        String key = keys.get(random.nextInt(keys.size()));
        ConfigurationSection r = list.getConfigurationSection(key);
        resource = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
        resourceName = Text.color(r == null ? list.getString(key, key) : r.getString("nom", key));
        // -1 : bloc sans stade de croissance (melon, citrouille, canne...)
        matureData = r == null ? defaultMature(resource) : r.getInt("data-mure", defaultMature(resource));
        plugin.msg().broadcast("farm.debut", "evenement", getName(), "ressource", resourceName,
                "minutes", durationMinutes());
        return null;
    }

    private static int defaultMature(Material m) {
        switch (m) {
            case CROPS:
            case CARROT:
            case POTATO:
                return 7;
            case NETHER_WARTS:
                return 3;
            default:
                return -1;
        }
    }

    @Override
    protected void onTick() {
        if (elapsedMillis() / 1000 % 5 != 0) {
            return;
        }
        String line = plugin.msg().text("farm.barre", "ressource", resourceName, "temps", time(remainingSeconds()),
                "top", top3());
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.msg().sendLine(p, line);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != resource || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (matureData >= 0 && block.getData() < matureData) {
            return;
        }
        Player player = event.getPlayer();
        String faction = plugin.factions().getFactionId(player);
        if (faction != null) {
            addScore(faction, 1);
        }
    }

    @Override
    protected void onStop(boolean cancelled) {
        if (cancelled) {
            plugin.msg().broadcast("evenement.annule", "evenement", getName());
            return;
        }
        rewardPodium();
    }
}

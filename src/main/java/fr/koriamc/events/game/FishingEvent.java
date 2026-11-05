package fr.koriamc.events.game;

import fr.koriamc.events.KoriaEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Concours de pêche : 15 minutes pour pêcher le plus de poissons en faction.
 * La canne du Toucan aide (poissons doublés, pêche plus rapide). Points au top 3.
 */
public final class FishingEvent extends GameEvent {

    public FishingEvent(KoriaEvents plugin) {
        super(plugin, "peche");
    }

    @Override
    public String questKey() {
        return "PECHE";
    }

    @Override
    protected String onStart() {
        plugin.msg().broadcast("peche.debut", "evenement", getName(), "minutes", durationMinutes());
        return null;
    }

    @Override
    protected void onTick() {
        if (elapsedMillis() / 1000 % 5 != 0) {
            return;
        }
        String line = plugin.msg().text("peche.barre", "temps", time(remainingSeconds()), "top", top3());
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.msg().sendLine(p, line);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item)) {
            return;
        }
        String faction = plugin.factions().getFactionId(event.getPlayer());
        if (faction != null) {
            addScore(faction, Math.max(1, ((Item) event.getCaught()).getItemStack().getAmount()));
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

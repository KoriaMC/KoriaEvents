package fr.koriamc.events.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Pont facultatif vers KoriaQuest (réflexion) : signale l'objectif GAGNER_EVENEMENT
 * avec la cible DRAPEAU, FARM ou PECHE.
 */
public final class QuestHook {

    private boolean available;
    private Method of;
    private Method key;
    private Method progress;

    public void register() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("KoriaQuest") == null) {
            return;
        }
        try {
            Class<?> api = Class.forName("fr.koriamc.quest.api.KoriaQuestAPI");
            Class<?> context = Class.forName("fr.koriamc.quest.objective.ProgressContext");
            of = context.getMethod("of", Player.class, String.class, long.class);
            key = context.getMethod("key", String.class);
            progress = api.getMethod("progress", context);
            available = true;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[KoriaEvents] KoriaQuest présent mais incompatible : " + t);
        }
    }

    public void onWin(String event, Collection<Player> winners) {
        if (!available) {
            return;
        }
        for (Player player : winners) {
            try {
                Object ctx = of.invoke(null, player, "GAGNER_EVENEMENT", 1L);
                progress.invoke(null, key.invoke(ctx, event));
            } catch (Throwable t) {
                available = false;
                return;
            }
        }
    }
}

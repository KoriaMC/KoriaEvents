package fr.koriamc.events.game;

import fr.koriamc.events.KoriaEvents;
import fr.koriamc.events.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Évènement de faction (drapeau, farm, pêche) : démarrage manuel ou planifié, durée maximale,
 * et pour les évènements à score, classement des factions et points aux 3 premières.
 */
public abstract class GameEvent implements Listener {

    protected final KoriaEvents plugin;
    private final String id;
    protected ConfigurationSection config;
    private boolean running;
    private long startedAt;
    /** Score par faction (évènements à classement). */
    protected final Map<String, Long> scores = new LinkedHashMap<String, Long>();

    protected GameEvent(KoriaEvents plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Clé de l'objectif GAGNER_EVENEMENT de KoriaQuest (DRAPEAU, FARM, PECHE). */
    public abstract String questKey();

    public void load(ConfigurationSection section) {
        this.config = section;
    }

    public ConfigurationSection config() {
        return config;
    }

    public boolean isEnabled() {
        return config != null && config.getBoolean("actif", true);
    }

    public String getName() {
        return Text.color(config == null ? id : config.getString("nom", id));
    }

    public boolean isRunning() {
        return running;
    }

    public long elapsedMillis() {
        return running ? System.currentTimeMillis() - startedAt : 0;
    }

    public long remainingSeconds() {
        long max = durationMinutes() * 60L;
        return Math.max(0, max - elapsedMillis() / 1000L);
    }

    protected int durationMinutes() {
        return Math.max(1, config.getInt("duree-minutes", 20));
    }

    /** @return un message d'erreur, ou null si l'évènement a démarré */
    public final String start() {
        if (running) {
            return "deja-lance";
        }
        if (!isEnabled()) {
            return "desactive";
        }
        String error = onStart();
        if (error != null) {
            return error;
        }
        running = true;
        startedAt = System.currentTimeMillis();
        scores.clear();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        return null;
    }

    /** Arrête l'évènement (sans vainqueur si {@code cancelled}). */
    public final void stop(boolean cancelled) {
        if (!running) {
            return;
        }
        running = false;
        HandlerList.unregisterAll(this);
        onStop(cancelled);
        scores.clear();
    }

    /** Appelé chaque seconde pendant l'évènement. */
    public final void tick() {
        if (!running) {
            return;
        }
        if (elapsedMillis() >= durationMinutes() * 60000L) {
            onTimeout();
            stop(false);
            return;
        }
        onTick();
    }

    /** Préparation ; renvoie une clé de message d'erreur pour refuser le démarrage. */
    protected abstract String onStart();

    protected abstract void onStop(boolean cancelled);

    protected void onTick() {
    }

    /** Fin du temps imparti (avant onStop). */
    protected void onTimeout() {
    }

    // ============================================================ Classement

    protected void addScore(String factionId, long amount) {
        Long current = scores.get(factionId);
        scores.put(factionId, (current == null ? 0 : current) + amount);
    }

    /** Factions triées par score décroissant. */
    protected List<Map.Entry<String, Long>> ranking() {
        List<Map.Entry<String, Long>> list = new ArrayList<Map.Entry<String, Long>>(scores.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        return list;
    }

    /** Points aux 3 premières factions (config "points-top") et annonce du podium. */
    protected void rewardPodium() {
        List<Long> points = new ArrayList<Long>();
        for (Object o : config.getList("points-top", Collections.emptyList())) {
            try {
                points.add(Long.parseLong(String.valueOf(o)));
            } catch (NumberFormatException ignored) {
                // valeur invalide ignorée
            }
        }
        List<Map.Entry<String, Long>> ranking = ranking();
        if (ranking.isEmpty()) {
            plugin.msg().broadcast("evenement.sans-vainqueur", "evenement", getName());
            return;
        }
        plugin.msg().broadcast("evenement.podium-entete", "evenement", getName());
        for (int i = 0; i < ranking.size() && i < 3; i++) {
            Map.Entry<String, Long> e = ranking.get(i);
            long reward = i < points.size() ? points.get(i) : 0;
            long applied = plugin.factions().addPoints(e.getKey(), reward, getId());
            plugin.msg().broadcast("evenement.podium-ligne", "rang", i + 1, "faction", plugin.factions().getTag(e.getKey()),
                    "score", Text.number(e.getValue(), 0), "points", applied);
        }
        plugin.quests().onWin(questKey(), plugin.factions().online(ranking.get(0).getKey()));
    }

    /** Victoire immédiate d'une faction (drapeau). */
    protected void rewardWinner(String factionId, Player by) {
        long points = config.getLong("points", 2000);
        long applied = plugin.factions().addPoints(factionId, points, getId());
        plugin.msg().broadcast("evenement.victoire", "evenement", getName(), "faction",
                plugin.factions().getTag(factionId), "joueur", by == null ? "?" : by.getName(), "points", applied);
        plugin.quests().onWin(questKey(), plugin.factions().online(factionId));
    }

    /** Ligne d'affichage du top 3 (barre d'action). */
    protected String top3() {
        StringBuilder sb = new StringBuilder();
        List<Map.Entry<String, Long>> ranking = ranking();
        for (int i = 0; i < ranking.size() && i < 3; i++) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(plugin.msg().format(plugin.msg().raw("evenement.top-entree").get(0), "rang", i + 1,
                    "faction", plugin.factions().getTag(ranking.get(i).getKey()),
                    "score", Text.number(ranking.get(i).getValue(), 0)));
        }
        return sb.length() == 0 ? plugin.msg().text("evenement.top-vide") : sb.toString();
    }

    protected static String time(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}

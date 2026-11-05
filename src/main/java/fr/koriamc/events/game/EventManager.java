package fr.koriamc.events.game;

import fr.koriamc.events.KoriaEvents;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les évènements, leur planning (jours + heures, fuseau Europe/Paris) et leur tick.
 */
public final class EventManager {

    private static final Map<String, DayOfWeek> DAYS = new HashMap<String, DayOfWeek>();

    static {
        DAYS.put("LUNDI", DayOfWeek.MONDAY);
        DAYS.put("MARDI", DayOfWeek.TUESDAY);
        DAYS.put("MERCREDI", DayOfWeek.WEDNESDAY);
        DAYS.put("JEUDI", DayOfWeek.THURSDAY);
        DAYS.put("VENDREDI", DayOfWeek.FRIDAY);
        DAYS.put("SAMEDI", DayOfWeek.SATURDAY);
        DAYS.put("DIMANCHE", DayOfWeek.SUNDAY);
    }

    private final KoriaEvents plugin;
    private final Map<String, GameEvent> events = new LinkedHashMap<String, GameEvent>();
    private ZoneId zone = ZoneId.of("Europe/Paris");
    private String lastMinute = "";

    public EventManager(KoriaEvents plugin) {
        this.plugin = plugin;
        register(new FlagEvent(plugin));
        register(new FarmEvent(plugin));
        register(new FishingEvent(plugin));
    }

    private void register(GameEvent event) {
        events.put(event.getId(), event);
    }

    public void load(ConfigurationSection c) {
        try {
            zone = ZoneId.of(c.getString("fuseau", "Europe/Paris"));
        } catch (Exception e) {
            zone = ZoneId.of("Europe/Paris");
        }
        ConfigurationSection section = c.getConfigurationSection("evenements");
        for (GameEvent event : events.values()) {
            event.load(section == null ? null : section.getConfigurationSection(event.getId()));
        }
    }

    public GameEvent get(String id) {
        return id == null ? null : events.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<GameEvent> all() {
        return Collections.unmodifiableCollection(events.values());
    }

    public void stopAll() {
        for (GameEvent event : events.values()) {
            event.stop(true);
        }
    }

    /** Chaque seconde : tick des évènements en cours et démarrages planifiés (une fois par minute). */
    public void tick() {
        for (GameEvent event : events.values()) {
            event.tick();
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        String minute = now.getDayOfWeek() + " " + String.format("%02d:%02d", now.getHour(), now.getMinute());
        if (minute.equals(lastMinute)) {
            return;
        }
        lastMinute = minute;
        String hour = String.format("%02d:%02d", now.getHour(), now.getMinute());
        for (GameEvent event : events.values()) {
            ConfigurationSection plan = event.config() == null ? null : event.config().getConfigurationSection("planning");
            if (plan == null || !plan.getBoolean("actif", false) || event.isRunning() || !event.isEnabled()) {
                continue;
            }
            if (matchesDay(plan.getStringList("jours"), now.getDayOfWeek()) && plan.getStringList("heures").contains(hour)) {
                String error = event.start();
                if (error != null) {
                    plugin.getLogger().warning("Démarrage planifié de " + event.getId() + " impossible : " + error);
                }
            }
        }
    }

    private static boolean matchesDay(List<String> days, DayOfWeek day) {
        for (String d : days) {
            if (day == DAYS.get(d.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}

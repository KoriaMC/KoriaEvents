package fr.koriamc.events.config;

import fr.koriamc.events.util.Nms;
import fr.koriamc.events.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Textes de messages.json. Préfixes de ligne : [actionbar] et [title]titre;;sous-titre.
 * {prefix} est remplacé par la valeur "prefix".
 */
public final class Messages {

    private ConfigurationSection cfg;
    private String prefix = "";

    public void load(ConfigurationSection cfg) {
        this.cfg = cfg;
        this.prefix = cfg.getString("prefix", "");
    }

    public boolean has(String key) {
        return cfg != null && cfg.contains(key);
    }

    /** Lignes brutes d'une clé (texte simple ou liste). */
    public List<String> raw(String key) {
        List<String> out = new ArrayList<String>();
        if (cfg == null || !cfg.contains(key)) {
            out.add(key);
        } else if (cfg.isList(key)) {
            out.addAll(cfg.getStringList(key));
        } else {
            out.add(cfg.getString(key, ""));
        }
        return out;
    }

    public String format(String line, Object... kv) {
        return Text.color(Text.replace(line.replace("{prefix}", prefix), kv));
    }

    /** Texte d'une clé, lignes jointes par des retours à la ligne. */
    public String text(String key, Object... kv) {
        StringBuilder sb = new StringBuilder();
        for (String line : raw(key)) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(format(line, kv));
        }
        return sb.toString();
    }

    public List<String> lines(String key, Object... kv) {
        List<String> out = new ArrayList<String>();
        for (String line : raw(key)) {
            out.add(format(line, kv));
        }
        return out;
    }

    public void send(CommandSender to, String key, Object... kv) {
        for (String line : raw(key)) {
            sendLine(to, format(line, kv));
        }
    }

    /** Envoie une ligne déjà formatée (gère [actionbar] et [title]). */
    public void sendLine(CommandSender to, String line) {
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("[actionbar]")) {
            if (to instanceof Player) {
                Nms.actionBar((Player) to, line.substring("[actionbar]".length()));
            }
            return;
        }
        if (line.startsWith("[title]")) {
            if (to instanceof Player) {
                String[] parts = line.substring("[title]".length()).split(";;", 2);
                Nms.title((Player) to, parts[0], parts.length > 1 ? parts[1] : "", 5, 40, 10);
            }
            return;
        }
        to.sendMessage(line);
    }

    public void broadcast(String key, Object... kv) {
        for (String line : raw(key)) {
            String formatted = format(line, kv);
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendLine(player, formatted);
            }
            if (!formatted.startsWith("[")) {
                Bukkit.getConsoleSender().sendMessage(formatted);
            }
        }
    }
}

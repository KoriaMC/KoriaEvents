package fr.koriamc.events.command;

import fr.koriamc.events.KoriaEvents;
import fr.koriamc.events.game.GameEvent;
import fr.koriamc.events.util.Locs;
import fr.koriamc.events.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /event list | start &lt;id&gt; | stop &lt;id&gt; | drapeau &lt;depart|arrivee&gt; | pnj [spawn|ajouter-warzone] | reload
 */
public final class EventCommand implements TabExecutor {

    private final KoriaEvents plugin;

    public EventCommand(KoriaEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            for (GameEvent event : plugin.events().all()) {
                plugin.msg().send(sender, event.isRunning() ? "commande.liste-en-cours" : "commande.liste",
                        "id", event.getId(), "evenement", event.getName(),
                        "temps", event.remainingSeconds() / 60);
            }
            return true;
        }
        if (!sender.hasPermission("koriaevents.admin")) {
            plugin.msg().send(sender, "commande.permission");
            return true;
        }
        if ((sub.equals("start") || sub.equals("stop")) && args.length >= 2) {
            GameEvent event = plugin.events().get(args[1]);
            if (event == null) {
                plugin.msg().send(sender, "commande.inconnu", "ids", ids());
                return true;
            }
            if (sub.equals("start")) {
                String error = event.start();
                plugin.msg().send(sender, error == null ? "commande.lance" : "erreurs." + error, "evenement", event.getName());
            } else {
                event.stop(true);
                plugin.msg().send(sender, "commande.arrete", "evenement", event.getName());
            }
            return true;
        }
        if (sub.equals("drapeau") && args.length >= 2 && sender instanceof Player) {
            String which = args[1].toLowerCase(Locale.ROOT);
            if (!which.equals("depart") && !which.equals("arrivee")) {
                plugin.msg().send(sender, "commande.aide");
                return true;
            }
            return save(sender, Arrays.asList("evenements", "drapeau", which), Locs.format(((Player) sender).getLocation()));
        }
        if (sub.equals("pnj")) {
            if (args.length >= 2 && sender instanceof Player) {
                String loc = Locs.format(((Player) sender).getLocation());
                if (args[1].equalsIgnoreCase("spawn")) {
                    return save(sender, Arrays.asList("pnj-medicaments", "spawn", "position"), loc);
                }
                if (args[1].equalsIgnoreCase("ajouter-warzone")) {
                    List<String> list = new ArrayList<String>(plugin.warzonePositions());
                    list.add(loc);
                    return save(sender, Arrays.asList("pnj-medicaments", "warzone", "positions"), list);
                }
            }
            for (String line : plugin.npcs().describe()) {
                sender.sendMessage(line);
            }
            return true;
        }
        if (sub.equals("reload")) {
            List<String> problems = plugin.reload();
            plugin.msg().send(sender, "commande.recharge", "problemes", problems.size());
            for (String p : problems) {
                sender.sendMessage("§c- " + p);
            }
            return true;
        }
        plugin.msg().send(sender, "commande.aide");
        return true;
    }

    private boolean save(CommandSender sender, List<String> path, Object value) {
        plugin.configFile().set(path, value);
        try {
            plugin.configFile().save();
        } catch (IOException e) {
            sender.sendMessage("§cÉcriture de config.json impossible : " + e.getMessage());
            return true;
        }
        plugin.reload();
        plugin.msg().send(sender, "commande.position-enregistree", "cle", Text.join(path, "."));
        return true;
    }

    private String ids() {
        List<String> ids = new ArrayList<String>();
        for (GameEvent e : plugin.events().all()) {
            ids.add(e.getId());
        }
        return Text.join(ids, ", ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(Collections.singletonList("list"));
            if (sender.hasPermission("koriaevents.admin")) {
                subs.addAll(Arrays.asList("start", "stop", "drapeau", "pnj", "reload"));
            }
            return Text.complete(args[0], subs);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("start") || sub.equals("stop")) {
                List<String> ids = new ArrayList<String>();
                for (GameEvent e : plugin.events().all()) {
                    ids.add(e.getId());
                }
                return Text.complete(args[1], ids);
            }
            if (sub.equals("drapeau")) {
                return Text.complete(args[1], Arrays.asList("depart", "arrivee"));
            }
            if (sub.equals("pnj")) {
                return Text.complete(args[1], Arrays.asList("spawn", "ajouter-warzone"));
            }
        }
        return Collections.emptyList();
    }
}

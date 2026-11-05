package fr.koriamc.events;

import fr.koriamc.events.command.EventCommand;
import fr.koriamc.events.config.JsonFile;
import fr.koriamc.events.config.Messages;
import fr.koriamc.events.game.EventManager;
import fr.koriamc.events.hook.FactionHook;
import fr.koriamc.events.hook.QuestHook;
import fr.koriamc.events.npc.MedicineNpcs;
import fr.koriamc.events.warzone.WarzoneListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * KoriaEvents : capture du drapeau, event Farm, concours de pêche, règles de la Warzone
 * et PNJ de vente de médicaments.
 */
public final class KoriaEvents extends JavaPlugin {

    private static KoriaEvents instance;

    private JsonFile configFile;
    private JsonFile messagesFile;
    private Configuration config;
    private final Messages messages = new Messages();
    private final FactionHook factions = new FactionHook();
    private final QuestHook quests = new QuestHook();
    private EventManager events;
    private WarzoneListener warzone;
    private MedicineNpcs npcs;

    public static KoriaEvents get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configFile = new JsonFile(this, "config.json", "evenements.farm.ressources", "pnj-medicaments.medicaments");
        messagesFile = new JsonFile(this, "messages.json");
        events = new EventManager(this);
        warzone = new WarzoneListener(this);
        npcs = new MedicineNpcs(this);
        quests.register();

        Bukkit.getPluginManager().registerEvents(warzone, this);
        Bukkit.getPluginManager().registerEvents(npcs, this);
        PluginCommand command = getCommand("event");
        if (command != null) {
            EventCommand executor = new EventCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        // les mondes sont chargés au premier tick : positions et PNJ après
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                for (String problem : reload()) {
                    getLogger().warning(problem);
                }
            }
        });
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                events.tick();
                npcs.tick();
            }
        }, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (events != null) {
            events.stopAll();
        }
        if (npcs != null) {
            npcs.despawnAll();
        }
        instance = null;
    }

    public List<String> reload() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : new JsonFile[]{configFile, messagesFile}) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messages.load(messagesFile.toConfiguration());
        events.load(config);
        warzone.load(config.getConfigurationSection("warzone"));
        npcs.load(config.getConfigurationSection("pnj-medicaments"), problems);
        return problems;
    }

    /** Positions actuelles des PNJ de la Warzone (config.json). */
    public List<String> warzonePositions() {
        return config == null ? new ArrayList<String>() : config.getStringList("pnj-medicaments.warzone.positions");
    }

    public JsonFile configFile() {
        return configFile;
    }

    public Messages msg() {
        return messages;
    }

    public FactionHook factions() {
        return factions;
    }

    public QuestHook quests() {
        return quests;
    }

    public EventManager events() {
        return events;
    }

    public MedicineNpcs npcs() {
        return npcs;
    }
}

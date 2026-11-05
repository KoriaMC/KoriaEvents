package fr.koriamc.events.npc;

import fr.koriamc.events.KoriaEvents;
import fr.koriamc.events.util.Locs;
import fr.koriamc.events.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * PNJ qui achètent des médicaments contre des points de classement de faction.
 * <ul>
 *   <li>spawn : position fixe, stock de 250 à 350 points par demande ;</li>
 *   <li>warzone : change de position toutes les 10 minutes, stock de 350 à 450 points.</li>
 * </ul>
 * Toutes les 30 minutes, chaque PNJ demande une nouvelle couleur de médicament
 * (256 à 1024 unités). Les points sont versés au prorata des médicaments livrés.
 */
public final class MedicineNpcs implements Listener {

    private static final String META = "koriaevents-pnj";

    /** Un PNJ et sa demande en cours. */
    private final class Npc {
        final String id;
        final List<Location> positions = new ArrayList<Location>();
        int minStock;
        int maxStock;
        int moveMinutes;
        Villager entity;
        int positionIndex;
        long lastMove;
        // demande
        Material wanted;
        String wantedName;
        int quantity;
        int delivered;
        int stock;
        int paid;

        Npc(String id) {
            this.id = id;
        }

        String name() {
            return Text.color(section(id).getString("nom", id));
        }

        int remaining() {
            return Math.max(0, quantity - delivered);
        }
    }

    private final KoriaEvents plugin;
    private final Random random = new Random();
    private final Map<String, Npc> npcs = new LinkedHashMap<String, Npc>();
    private final Map<Material, String> medicines = new LinkedHashMap<Material, String>();
    private ConfigurationSection config;
    private int renewMinutes;
    private int minQuantity;
    private int maxQuantity;
    private long lastRenew;

    public MedicineNpcs(KoriaEvents plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection section(String id) {
        ConfigurationSection s = config.getConfigurationSection(id);
        return s == null ? new org.bukkit.configuration.MemoryConfiguration() : s;
    }

    /** @param problems liste alimentée avec les erreurs de configuration */
    public void load(ConfigurationSection c, List<String> problems) {
        despawnAll();
        npcs.clear();
        medicines.clear();
        config = c;
        if (c == null || !c.getBoolean("actif", true)) {
            return;
        }
        renewMinutes = Math.max(1, c.getInt("renouvellement-minutes", 30));
        List<Integer> q = c.getIntegerList("quantite");
        minQuantity = q.size() > 0 ? q.get(0) : 256;
        maxQuantity = q.size() > 1 ? q.get(1) : 1024;
        ConfigurationSection meds = c.getConfigurationSection("medicaments");
        if (meds != null) {
            for (String key : meds.getKeys(false)) {
                Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (m == null) {
                    problems.add("config.json : pnj-medicaments.medicaments." + key
                            + " : objet inconnu (à ajouter au serveur KoriaMC).");
                } else {
                    medicines.put(m, Text.color(meds.getString(key, key)));
                }
            }
        }
        for (String id : new String[]{"spawn", "warzone"}) {
            ConfigurationSection s = c.getConfigurationSection(id);
            if (s == null || !s.getBoolean("actif", true)) {
                continue;
            }
            Npc npc = new Npc(id);
            List<String> raw = new ArrayList<String>(s.getStringList("positions"));
            if (s.isString("position")) {
                raw.add(0, s.getString("position"));
            }
            for (String p : raw) {
                Location l = Locs.parse(p);
                if (l == null) {
                    problems.add("config.json : pnj-medicaments." + id + " : position invalide '" + p + "' (monde,x,y,z).");
                } else {
                    npc.positions.add(Locs.center(l));
                }
            }
            List<Integer> stock = s.getIntegerList("stock-points");
            npc.minStock = stock.size() > 0 ? stock.get(0) : 250;
            npc.maxStock = stock.size() > 1 ? stock.get(1) : 350;
            npc.moveMinutes = Math.max(0, s.getInt("deplacement-minutes", 0));
            if (!npc.positions.isEmpty()) {
                npcs.put(id, npc);
            }
        }
        if (medicines.isEmpty()) {
            return;
        }
        for (Npc npc : npcs.values()) {
            newDemand(npc);
            spawn(npc);
        }
        lastRenew = System.currentTimeMillis();
    }

    private void newDemand(Npc npc) {
        List<Material> list = new ArrayList<Material>(medicines.keySet());
        npc.wanted = list.get(random.nextInt(list.size()));
        npc.wantedName = medicines.get(npc.wanted);
        npc.quantity = minQuantity + random.nextInt(Math.max(1, maxQuantity - minQuantity + 1));
        npc.delivered = 0;
        npc.stock = npc.minStock + random.nextInt(Math.max(1, npc.maxStock - npc.minStock + 1));
        npc.paid = 0;
        updateName(npc);
    }

    // ============================================================= Entités

    private void spawn(Npc npc) {
        Location at = npc.positions.get(npc.positionIndex % npc.positions.size());
        // nettoie un ancien PNJ resté après un arrêt brutal
        for (Entity e : at.getWorld().getNearbyEntities(at, 2, 2, 2)) {
            if (e.getType() == EntityType.VILLAGER && e.getCustomName() != null
                    && e.getCustomName().startsWith(npc.name())) {
                e.remove();
            }
        }
        Villager v = (Villager) at.getWorld().spawnEntity(at, EntityType.VILLAGER);
        v.setProfession(Villager.Profession.PRIEST);
        v.setCustomNameVisible(true);
        v.setRemoveWhenFarAway(false);
        v.setMetadata(META, new FixedMetadataValue(plugin, npc.id));
        noAi(v);
        npc.entity = v;
        npc.lastMove = System.currentTimeMillis();
        updateName(npc);
    }

    /** Coupe l'IA du villageois (NBT NoAI) ; sinon il est replacé chaque seconde. */
    private static void noAi(Entity entity) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            Class<?> nbtClass = Class.forName(handle.getClass().getPackage().getName() + ".NBTTagCompound");
            Object tag = nbtClass.newInstance();
            Method save = handle.getClass().getMethod("e", nbtClass);
            save.invoke(handle, tag);
            nbtClass.getMethod("setInt", String.class, int.class).invoke(tag, "NoAI", 1);
            nbtClass.getMethod("setInt", String.class, int.class).invoke(tag, "Silent", 1);
            handle.getClass().getMethod("f", nbtClass).invoke(handle, tag);
        } catch (Throwable ignored) {
            // la position est corrigée par tick()
        }
    }

    private void updateName(Npc npc) {
        if (npc.entity == null || npc.wanted == null) {
            return;
        }
        npc.entity.setCustomName(npc.name() + " " + plugin.msg().format(plugin.msg().raw("pnj.nom-demande").get(0),
                "medicament", npc.wantedName, "restant", npc.remaining()));
    }

    public void despawnAll() {
        for (Npc npc : npcs.values()) {
            if (npc.entity != null) {
                npc.entity.remove();
                npc.entity = null;
            }
        }
    }

    /** Chaque seconde : renouvellement des demandes, déplacement, PNJ immobile. */
    public void tick() {
        if (npcs.isEmpty() || medicines.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRenew >= renewMinutes * 60000L) {
            lastRenew = now;
            for (Npc npc : npcs.values()) {
                newDemand(npc);
            }
            plugin.msg().broadcast("pnj.renouvellement");
        }
        for (Npc npc : npcs.values()) {
            if (npc.moveMinutes > 0 && npc.positions.size() > 1 && now - npc.lastMove >= npc.moveMinutes * 60000L) {
                int next = npc.positionIndex;
                while (next == npc.positionIndex) {
                    next = random.nextInt(npc.positions.size());
                }
                npc.positionIndex = next;
                if (npc.entity != null) {
                    npc.entity.remove();
                }
                spawn(npc);
                plugin.msg().broadcast("pnj.deplacement", "pnj", npc.name());
                continue;
            }
            if (npc.entity == null || npc.entity.isDead() || !npc.entity.isValid()) {
                if (npc.positions.get(npc.positionIndex).getChunk().isLoaded()) {
                    spawn(npc);
                }
                continue;
            }
            Location home = npc.positions.get(npc.positionIndex);
            if (npc.entity.getLocation().distanceSquared(home) > 0.25) {
                npc.entity.teleport(home);
            }
        }
    }

    // ============================================================ Ventes

    private Npc of(Entity entity) {
        if (!entity.hasMetadata(META)) {
            return null;
        }
        return npcs.get(entity.getMetadata(META).get(0).asString());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Npc npc = of(event.getRightClicked());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (npc.remaining() <= 0) {
            plugin.msg().send(player, "pnj.demande-terminee", "pnj", npc.name());
            return;
        }
        String faction = plugin.factions().getFactionId(player);
        if (faction == null) {
            plugin.msg().send(player, "pnj.sans-faction");
            return;
        }
        int have = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == npc.wanted) {
                have += item.getAmount();
            }
        }
        if (have <= 0) {
            plugin.msg().send(player, "pnj.demande", "pnj", npc.name(), "medicament", npc.wantedName,
                    "restant", npc.remaining(), "stock", npc.stock - npc.paid);
            return;
        }
        int give = Math.min(have, npc.remaining());
        player.getInventory().removeItem(new ItemStack(npc.wanted, give));
        npc.delivered += give;
        // points au prorata ; la dernière livraison prend le reste du stock
        int due = npc.remaining() == 0 ? npc.stock - npc.paid
                : (int) Math.floor((double) npc.stock * npc.delivered / npc.quantity) - npc.paid;
        npc.paid += Math.max(0, due);
        long applied = plugin.factions().addPoints(faction, Math.max(0, due), "pnj-" + npc.id);
        plugin.msg().send(player, "pnj.vente", "quantite", give, "medicament", npc.wantedName, "points", applied);
        if (npc.remaining() == 0) {
            plugin.msg().broadcast("pnj.demande-completee", "pnj", npc.name(),
                    "faction", plugin.factions().getTag(faction));
        }
        updateName(npc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (of(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && of(event.getTarget()) != null) {
            event.setCancelled(true);
        }
    }

    /** Positions actuelles (commande /event pnj). */
    public List<String> describe() {
        List<String> out = new ArrayList<String>();
        for (Npc npc : npcs.values()) {
            out.add(plugin.msg().text("pnj.info", "pnj", npc.name(),
                    "position", Locs.pretty(npc.positions.get(npc.positionIndex)),
                    "medicament", npc.wantedName == null ? "?" : npc.wantedName,
                    "restant", npc.remaining(), "stock", npc.stock - npc.paid));
        }
        return out;
    }
}

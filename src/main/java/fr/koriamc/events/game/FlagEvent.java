package fr.koriamc.events.game;

import fr.koriamc.events.KoriaEvents;
import fr.koriamc.events.util.Locs;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.UUID;

/**
 * Capture du drapeau : le drapeau est posé sur le bloc de départ, un joueur de faction le
 * récupère et doit l'amener jusqu'à l'arrivée en moins de 5 minutes. S'il meurt, se
 * déconnecte ou dépasse le temps, le drapeau revient au départ. Le porteur ne peut pas
 * utiliser d'arc avec Punch. La première faction qui dépose le drapeau gagne.
 */
@SuppressWarnings("deprecation")
public final class FlagEvent extends GameEvent {

    private Location start;
    private Location arrival;
    private UUID carrier;
    private String carrierFaction;
    private long pickedAt;
    private ItemStack savedHelmet;
    private Location savedCompass;

    public FlagEvent(KoriaEvents plugin) {
        super(plugin, "drapeau");
    }

    @Override
    public String questKey() {
        return "DRAPEAU";
    }

    @Override
    protected int durationMinutes() {
        return Math.max(1, config.getInt("duree-minutes", 30));
    }

    @Override
    protected String onStart() {
        start = Locs.parse(config.getString("depart"));
        arrival = Locs.parse(config.getString("arrivee"));
        if (start == null || arrival == null) {
            return "drapeau-positions";
        }
        carrier = null;
        placeFlag();
        plugin.msg().broadcast("drapeau.debut", "evenement", getName(), "depart", Locs.pretty(start),
                "arrivee", Locs.pretty(arrival), "minutes", carrySeconds() / 60);
        return null;
    }

    private int carrySeconds() {
        return Math.max(10, config.getInt("duree-port-secondes", 300));
    }

    private void placeFlag() {
        Block block = start.getBlock();
        block.setType(Material.STANDING_BANNER);
        BlockState state = block.getState();
        if (state instanceof Banner) {
            ((Banner) state).setBaseColor(DyeColor.RED);
            state.update(true);
        }
    }

    private void removeFlagBlock() {
        Block block = start.getBlock();
        if (block.getType() == Material.STANDING_BANNER || block.getType() == Material.WALL_BANNER) {
            block.setType(Material.AIR);
        }
    }

    private boolean isFlag(Block block) {
        return block != null && start != null && block.getLocation().equals(start.getBlock().getLocation())
                && (block.getType() == Material.STANDING_BANNER || block.getType() == Material.WALL_BANNER);
    }

    private static ItemStack flagItem() {
        ItemStack banner = new ItemStack(Material.BANNER, 1, (short) DyeColor.RED.getDyeData());
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.setBaseColor(DyeColor.RED);
        meta.setDisplayName("§c§lDrapeau");
        banner.setItemMeta(meta);
        return banner;
    }

    // =========================================================== Ramassage

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isFlag(event.getClickedBlock())) {
            event.setCancelled(true);
            pickUp(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (isFlag(event.getBlock())) {
            event.setCancelled(true);
            pickUp(event.getPlayer());
        }
    }

    private void pickUp(Player player) {
        if (carrier != null) {
            return;
        }
        String faction = plugin.factions().getFactionId(player);
        if (faction == null) {
            plugin.msg().send(player, "drapeau.sans-faction");
            return;
        }
        carrier = player.getUniqueId();
        carrierFaction = faction;
        pickedAt = System.currentTimeMillis();
        savedHelmet = player.getInventory().getHelmet();
        savedCompass = player.getCompassTarget();
        player.getInventory().setHelmet(flagItem());
        player.setCompassTarget(arrival);
        removeFlagBlock();
        plugin.msg().broadcast("drapeau.ramasse", "joueur", player.getName(),
                "faction", plugin.factions().getTag(faction), "arrivee", Locs.pretty(arrival));
        plugin.msg().send(player, "drapeau.porteur", "secondes", carrySeconds(), "arrivee", Locs.pretty(arrival));
    }

    // ============================================================ Porteur

    private Player carrierPlayer() {
        return carrier == null ? null : Bukkit.getPlayer(carrier);
    }

    /** Rend le casque du porteur et remet le drapeau au départ. */
    private void returnFlag(String reasonKey) {
        Player player = carrierPlayer();
        if (player != null) {
            restore(player);
        }
        carrier = null;
        carrierFaction = null;
        placeFlag();
        if (reasonKey != null) {
            plugin.msg().broadcast(reasonKey, "joueur", player == null ? "?" : player.getName());
        }
    }

    private void restore(Player player) {
        player.getInventory().setHelmet(savedHelmet);
        if (savedCompass != null) {
            player.setCompassTarget(savedCompass);
        }
        savedHelmet = null;
    }

    @Override
    protected void onTick() {
        if (carrier == null) {
            return;
        }
        Player player = carrierPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            returnFlag("drapeau.perdu");
            return;
        }
        long held = (System.currentTimeMillis() - pickedAt) / 1000L;
        if (held >= carrySeconds()) {
            returnFlag("drapeau.temps-ecoule");
            return;
        }
        double radius = Math.max(1, config.getDouble("rayon-arrivee", 2));
        Location at = player.getLocation();
        if (at.getWorld().equals(arrival.getWorld()) && at.distanceSquared(Locs.center(arrival)) <= radius * radius) {
            String faction = carrierFaction;
            restore(player);
            carrier = null;
            rewardWinner(faction, player);
            stop(false);
            return;
        }
        plugin.msg().send(player, "drapeau.barre", "temps", time(carrySeconds() - held),
                "distance", (int) Math.sqrt(at.getWorld().equals(arrival.getWorld()) ? at.distanceSquared(arrival) : 0));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!player.getUniqueId().equals(carrier)) {
            return;
        }
        // le drapeau ne tombe pas : le casque d'origine tombe à sa place
        event.getDrops().remove(player.getInventory().getHelmet());
        if (savedHelmet != null && savedHelmet.getType() != Material.AIR) {
            event.getDrops().add(savedHelmet);
        }
        savedHelmet = null;
        player.getInventory().setHelmet(null);
        carrier = null;
        carrierFaction = null;
        placeFlag();
        plugin.msg().broadcast("drapeau.porteur-mort", "joueur", player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(carrier)) {
            returnFlag("drapeau.perdu");
        }
    }

    /** Le porteur ne peut pas retirer le drapeau de sa tête. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getUniqueId().equals(carrier) && event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }
    }

    /** Pas d'arc avec Punch pour le porteur. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShoot(EntityShootBowEvent event) {
        if (!event.getEntity().getUniqueId().equals(carrier) || event.getBow() == null) {
            return;
        }
        if (event.getBow().getEnchantmentLevel(Enchantment.ARROW_KNOCKBACK) > 0) {
            event.setCancelled(true);
            plugin.msg().send((Player) event.getEntity(), "drapeau.punch-interdit");
        }
    }

    /** Le porteur ne peut pas se téléporter (commandes, perles) si la config l'interdit. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.getPlayer().getUniqueId().equals(carrier) || !config.getBoolean("porteur-teleportation-interdite", true)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.COMMAND
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
            plugin.msg().send(event.getPlayer(), "drapeau.teleportation-interdite");
        }
    }

    @Override
    protected void onTimeout() {
        plugin.msg().broadcast("evenement.sans-vainqueur", "evenement", getName());
    }

    @Override
    protected void onStop(boolean cancelled) {
        Player player = carrierPlayer();
        if (player != null) {
            restore(player);
        }
        carrier = null;
        carrierFaction = null;
        removeFlagBlock();
        if (cancelled) {
            plugin.msg().broadcast("evenement.annule", "evenement", getName());
        }
    }
}

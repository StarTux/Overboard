package com.cavetale.overboard;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final OverboardPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.save.state != State.PLAY) return;
        Player player = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.die(player);
                player.setHealth(20.0);
            });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.save.state == State.PLAY) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getPlayer().setGameMode(GameMode.SPECTATOR);
                }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerJoinEvent event) {
        if (plugin.save.state == State.PLAY) {
            plugin.save.players.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        if (plugin.save.state != State.PLAY && plugin.save.state != State.WARMUP) return;
        Player player = event.getPlayer();
        List<String> ls = new ArrayList<>();
        ls.add("" + ChatColor.DARK_RED + ChatColor.BOLD + "Overboard!");
        ls.add(ChatColor.GRAY + "Sprint + Cutlass Hit");
        ls.add(ChatColor.GRAY + " = Extra Knockback");
        ls.add("");
        String all = plugin.getAlivePlayers().stream()
            .map(s -> s.getName())
            .sorted()
            .collect(Collectors.joining(" "));
        List<String> alls = wrap(all, 24);
        if (alls.size() > 3) {
            alls = alls.subList(0, 3);
        }
        ls.addAll(alls);
        event.addLines(plugin, Priority.HIGHEST, ls);
    }

    public static List<String> wrap(String what, final int maxLineLength) {
        String[] words = what.split("\\s+");
        List<String> lines = new ArrayList<>();
        if (words.length == 0) return lines;
        StringBuilder line = new StringBuilder(words[0]);
        int lineLength = ChatColor.stripColor(words[0]).length();
        for (int i = 1; i < words.length; ++i) {
            String word = words[i];
            int wordLength = ChatColor.stripColor(word).length();
            if (lineLength + wordLength + 1 > maxLineLength) {
                lines.add(line.toString());
                line = new StringBuilder(word);
                lineLength = wordLength;
            } else {
                line.append(" ");
                line.append(word);
                lineLength += wordLength + 1;
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }


    @EventHandler
    void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            event.getEntity().teleport(plugin.world.getSpawnLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.save.state != State.PLAY) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Vec3i vec = Vec3i.of(event.getBlock());
        if (!plugin.gameRegion.contains(vec)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        if (plugin.save.state != State.PLAY) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Player player = event.getPlayer();
        Pirate pirate = plugin.save.getPirate(player);
        if (pirate == null) return;
        if (Tag.BEDS.isTagged(event.getBlock().getType())) {
            pirate.bed1 = Vec3i.of(event.getReplacedBlockStates().get(0).getBlock());
            pirate.bed2 = Vec3i.of(event.getReplacedBlockStates().get(1).getBlock());
            player.sendMessage(Component.text("Bed spawn location was set!", NamedTextColor.GREEN));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onBlockBreak(BlockBreakEvent event) {
        if (plugin.save.state != State.PLAY) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Vec3i vec = Vec3i.of(event.getBlock());
        if (!plugin.gameRegion.contains(vec)) {
            event.setCancelled(true);
            return;
        }
        onBlockBreak(vec);
    }

    void onBlockBreak(Vec3i vec) {
        for (Map.Entry<UUID, Pirate> entry : new ArrayList<>(plugin.save.players.entrySet())) {
            Pirate pirate = entry.getValue();
            if (Objects.equals(pirate.bed1, vec) || Objects.equals(pirate.bed2, vec)) {
                pirate.bed1 = null;
                pirate.bed2 = null;
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    player.sendMessage(Component.text("Your bed was broken!", NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler
    void onBlockPreDispense(BlockPreDispenseEvent event) {
        if (plugin.save.state != State.PLAY) return;
        Block block = event.getBlock();
        Vec3i vec = Vec3i.of(block);
        if (!plugin.gameRegion.contains(vec)) return;
        ItemStack itemStack = event.getItemStack();
        if (itemStack.getType() != Material.TNT) return;
        event.setCancelled(true);
        if (plugin.save.cannonAt(vec) != null) return;
        itemStack.subtract(1);
        Location loc = vec.toBlock(plugin.world).getLocation().add(0.5, 1.5, 0.5);
        ArmorStand armorStand = plugin.world.spawn(loc, ArmorStand.class, e -> {
                e.setPersistent(false);
                e.setSmall(true);
                e.setMarker(true);
                e.setInvisible(true);
                e.setCustomNameVisible(true);
                e.customName(Component.text("" + 3, NamedTextColor.YELLOW));
            });
        Cannon cannon = new Cannon(vec, 20 * 3, armorStand == null ? null : armorStand.getUniqueId());
        plugin.save.cannons.add(cannon);
    }

    @EventHandler
    void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.save.state != State.PLAY) {
            event.blockList().clear();
            event.setCancelled(true);
            return;
        }
        Iterator<Block> iter = event.blockList().iterator();
        while (iter.hasNext()) {
            Block block = iter.next();
            Vec3i vec = Vec3i.of(block);
            if (!plugin.gameRegion.contains(vec)) {
                iter.remove();
            } else {
                onBlockBreak(vec);
            }
        }
    }

    @EventHandler
    void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.save.state != State.PLAY) {
            event.blockList().clear();
            event.setCancelled(true);
            return;
        }
        Iterator<Block> iter = event.blockList().iterator();
        while (iter.hasNext()) {
            Block block = iter.next();
            Vec3i vec = Vec3i.of(block);
            if (!plugin.gameRegion.contains(vec)) {
                iter.remove();
            } else {
                onBlockBreak(vec);
            }
        }
    }
}

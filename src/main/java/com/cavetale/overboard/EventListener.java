package com.cavetale.overboard;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.struct.Vec3i;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final OverboardPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.save.state != State.GAME) return;
        Player player = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.die(player);
                player.setHealth(20.0);
            });
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.save.state == State.IDLE) {
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.startPlayer(player));
        }
    }

    @EventHandler
    private  void onPlayerQuit(PlayerJoinEvent event) {
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>();
        lines.add(plugin.TITLE);
        if (plugin.save.state == State.GAME) {
            lines.add(textOfChildren(text(tiny("time"), GRAY), text(" " + plugin.timeString, AQUA)));
            lines.add(textOfChildren(text(tiny("fire spread"), GRAY), text(" " + plugin.save.tickSpeed, RED)));
            lines.add(textOfChildren(text(tiny("players"), GRAY), text(" " + plugin.playerCount, RED)));
            Pirate pirate = plugin.save.pirates.get(event.getPlayer().getUniqueId());
            if (pirate != null) {
                lines.add(textOfChildren(text(tiny("deaths"), GRAY), text(" " + pirate.deaths, RED)));
            }
        } else if (plugin.save.state == State.WARMUP) {
            lines.add(textOfChildren(text(tiny("countdown"), GRAY), text(" " + plugin.timeString, AQUA)));
        } else if (plugin.save.state == State.END) {
            if (plugin.save.winner != null) {
                lines.add(textOfChildren(text(tiny("winner"), GRAY), text(" " + PlayerCache.nameForUuid(plugin.save.winner), GREEN)));
            } else {
                lines.add(text("draw!", DARK_RED, BOLD));
            }
        } else {
            if (plugin.save.event && plugin.highscoreLines != null) {
                lines.addAll(plugin.highscoreLines);
            }
        }
        event.sidebar(PlayerHudPriority.HIGH, lines);
    }

    @EventHandler
    private void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.isGameWorld(event.getEntity().getWorld())) return;
        switch (event.getCause()) {
        case VOID:
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> event.getEntity().teleport(plugin.world.getSpawnLocation()));
            break;
        case ENTITY_ATTACK:
        case ENTITY_SWEEP_ATTACK:
            event.setDamage(0);
            break;
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isGameWorld(event.getBlock().getWorld())) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        Vec3i vec = Vec3i.of(event.getBlock());
        if (plugin.save.state != State.GAME) {
            event.setCancelled(true);
        } else if (!plugin.isGameArea(vec)) {
            event.setCancelled(true);
        } else if (plugin.isDeathArea(vec)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isGameWorld(event.getBlock().getWorld())) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        Vec3i vec = Vec3i.of(event.getBlock());
        if (plugin.save.state != State.GAME) {
            event.setCancelled(true);
        } else if (!plugin.isGameArea(vec)) {
            event.setCancelled(true);
        } else if (plugin.isDeathArea(vec)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.isGameWorld(event.getBlock().getWorld())) return;
        if (plugin.save.state != State.GAME) {
            event.setCancelled(true);
            return;
        }
        for (Block block : List.copyOf(event.blockList())) {
            Vec3i vec = Vec3i.of(block);
            if (!plugin.isGameArea(vec)) {
                event.blockList().remove(block);
            } else if (plugin.isDeathArea(vec)) {
                event.blockList().remove(block);
            }
        }
    }

    @EventHandler
    private void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.isGameWorld(event.getEntity().getWorld())) return;
        if (plugin.save.state != State.GAME) {
            event.setCancelled(true);
            return;
        }
        for (Block block : List.copyOf(event.blockList())) {
            Vec3i vec = Vec3i.of(block);
            if (!plugin.isGameArea(vec)) {
                event.blockList().remove(block);
            } else if (plugin.isDeathArea(vec)) {
                event.blockList().remove(block);
            }
        }
    }

    @EventHandler
    private void onBlockFromTo(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.FIRE) return;
        final int radius = 2;
        int fireBlocks = 0;
        for (int dy = -radius; dy <= radius; dy += 1) {
            for (int dz = -radius; dz <= radius; dz += 1) {
                for (int dx = -radius; dx <= radius; dx += 1) {
                    if (event.getToBlock().getRelative(dx, dy, dz).getType() == Material.FIRE) {
                        fireBlocks += 1;
                        if (fireBlocks > 2) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (plugin.save.state == State.IDLE) {
            event.setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }
}

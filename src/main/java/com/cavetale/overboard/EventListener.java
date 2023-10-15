package com.cavetale.overboard;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.player.PlayerTeamQuery;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.Mytems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.TNT;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
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
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.save.state == State.IDLE) {
            event.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        } else {
            if (!plugin.isGameWorld(player.getWorld())) return;
            event.setRespawnLocation(player.getLocation());
        }
    }

    @EventHandler
    private void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (plugin.save.state == State.IDLE) {
            event.setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.startPlayer(player);
                });
        }
    }

    @EventHandler
    private void onPlayerQuit(PlayerJoinEvent event) {
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>();
        lines.add(plugin.TITLE);
        Pirate pirate = plugin.save.pirates.get(event.getPlayer().getUniqueId());
        if (plugin.save.state == State.WARMUP || plugin.save.state == State.GAME) {
            if (plugin.save.useTeams && pirate.team != null) {
                lines.add(textOfChildren(text(tiny("team "), GRAY), pirate.team.component));
            }
        }
        if (plugin.save.state == State.GAME) {
            lines.add(textOfChildren(text(tiny("time"), GRAY), text(" " + plugin.timeString, AQUA)));
            lines.add(textOfChildren(text(tiny("fire spread"), GRAY), text(" x" + plugin.save.tickSpeed / 3, RED)));
            if (plugin.save.useTeams) {
                Map<PirateTeam, Integer> aliveTeams = plugin.countAliveTeams();
                for (PirateTeam team : PirateTeam.values()) {
                    lines.add(textOfChildren(text(tiny(team.key.toLowerCase()), GRAY), text(" " + aliveTeams.getOrDefault(team, 0), team.color)));
                }
            } else {
                lines.add(textOfChildren(text(tiny("players"), GRAY), text(" " + plugin.playerCount, RED)));
            }
        }
        if (plugin.save.state == State.WARMUP || plugin.save.state == State.GAME) {
            if (pirate != null) {
                lines.add(textOfChildren(text(tiny("deaths"), GRAY), text(" " + pirate.deaths, RED)));
                if (plugin.save.useTeams && pirate.team != null) {
                    for (Pirate other : plugin.getTeamPirates(pirate.team)) {
                        String dname = "\u2588 " + other.name;
                        if (!other.playing || (other.dead && other.respawnCooldown > 0)) {
                            lines.add(text(dname, DARK_GRAY));
                        } else if (other.dead) {
                            lines.add(text(dname, GRAY));
                        } else {
                            lines.add(text(dname, other.team.color));
                        }
                    }
                }
            }
        }
        if (plugin.save.state == State.WARMUP) {
            lines.add(textOfChildren(text(tiny("countdown"), GRAY), text(" " + plugin.timeString, AQUA)));
        } else if (plugin.save.state == State.END) {
            if (plugin.save.useTeams && plugin.save.winnerTeam != null) {
                lines.add(textOfChildren(text(tiny("winner "), GRAY), plugin.save.winnerTeam.component));
            } else if (plugin.save.winners != null && !plugin.save.winners.isEmpty()) {
                lines.add(textOfChildren(text(tiny("winner"), GRAY), text(" " + PlayerCache.nameForUuid(plugin.save.winners.get(0)), GREEN)));
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

    @EventHandler
    private void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!plugin.isGameWorld(event.getEntity().getWorld())) return;
        if (!plugin.save.useTeams) return;
        if (!(event.getEntity() instanceof Player damaged)) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        Pirate a = plugin.save.pirates.get(damaged.getUniqueId());
        if (a == null || !a.playing || a.team == null) return;
        Pirate b = plugin.save.pirates.get(damager.getUniqueId());
        if (b == null || !b.playing || b.team == null) return;
        if (a.team == b.team) event.setCancelled(true);
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
        if (event.getBlock().getType() == Material.TNT) {
            Block block = event.getBlock();
            Bukkit.getScheduler().runTask(plugin, () -> {
                    if (block.getType() != Material.TNT) return;
                    TNT tnt = (TNT) block.getBlockData();
                    tnt.setUnstable(true);
                    block.setBlockData(tnt);
                });
        } else if (event.getBlock().getType() == Material.FIRE || event.getBlock().getType() == Material.LADDER) {
            return;
        } else {
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
    private void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.isGameWorld(event.getBlock().getWorld())) return;
        if (event.getBlockClicked().getType() == Material.WATER) {
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
    private void onBlockForm(BlockFormEvent event) {
        if (!plugin.isGameWorld(event.getBlock().getWorld())) return;
        switch (event.getNewState().getType()) {
        case OBSIDIAN:
        case STONE:
        case COBBLESTONE:
        case ICE:
        case SNOW:
            event.setCancelled(true);
        default: break;
        }
    }

    @EventHandler
    private void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.isGameWorld(event.getBlock().getWorld())) return;
        if (plugin.random.nextInt(5) > 0) return;
        final float strength = 2f;
        final boolean fire = false;
        final boolean breakBlocks = true;
        if (plugin.random.nextInt(4) == 0) return;
        plugin.world.createExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), strength, fire, breakBlocks);
    }

    @EventHandler
    private void onPlayerTeam(PlayerTeamQuery query) {
        if (!plugin.save.useTeams) return;
        if (plugin.save.state == State.IDLE) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Pirate pirate = plugin.save.pirates.get(player.getUniqueId());
            if (pirate == null || pirate.team == null) continue;
            query.setTeam(player, pirate.team.queryTeam);
        }
    }

    @EventHandler
    private void onPlayerUseItem(PlayerInteractEvent event) {
        if (plugin.save.state != State.GAME) return;
        final Player player = event.getPlayer();
        if (!plugin.isGameWorld(player.getWorld())) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK: case RIGHT_CLICK_AIR: break;
        default: return;
        }
        if (!event.hasItem()) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Pirate pirate = plugin.save.pirates.get(player.getUniqueId());
        if (pirate == null) return;
        switch (item.getType()) {
        case TOTEM_OF_UNDYING: {
            if (!plugin.save.useTeams || pirate.team == null) return;
            Pirate thePirate = null;
            Player thePlayer = null;
            int chance = 1;
            for (Pirate other : plugin.getTeamPirates(pirate.team)) {
                if (other == pirate) continue;
                if (!other.playing) continue;
                Player otherPlayer = Bukkit.getPlayer(other.uuid);
                if (otherPlayer == null) continue;
                if (otherPlayer.getGameMode() != GameMode.SPECTATOR) continue;
                if (plugin.random.nextInt(chance) > 0) continue;
                thePirate = other;
                thePlayer = otherPlayer;
                chance += 1;
            }
            if (thePlayer == null || !plugin.respawnPlayer(thePlayer, player.getLocation())) {
                player.sendMessage(text("No player on your team is ready to respawn", RED));
                return;
            } else {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    other.sendMessage(textOfChildren(Mytems.BLUNDERBUSS,
                                                     text(" " + player.getName() + " respawned " + thePlayer.getName()
                                                          + " for team " + pirate.team.key, pirate.team.color)));
                }
            }
            item.subtract(1);
            event.setCancelled(true);
            if (plugin.save.event) {
                plugin.save.addScore(player.getUniqueId(), 1);
                plugin.computeHighscores();
            }
            break;
        }
        default: break;
        }
    }
}

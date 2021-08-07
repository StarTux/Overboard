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
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
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
        List<Component> ls = new ArrayList<>();
        ls.add(Component.text("Overboard!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Pirate pirate = plugin.save.getPirate(player);
        if (pirate != null) {
            ls.add(Component.text()
                   .append(Component.text("Your team ", NamedTextColor.GRAY))
                   .append(Component.text(pirate.team.displayName, pirate.team.color))
                   .build());
            ls.add(Component.text()
                   .append(Component.text("Your score ", NamedTextColor.GRAY))
                   .append(Component.text("" + pirate.score, NamedTextColor.WHITE))
                   .build());
            ls.add(Component.text()
                   .append(Component.text("Bed placed ", NamedTextColor.GRAY))
                   .append(Component.text(pirate.bed1 != null ? "Yes" : "No", NamedTextColor.WHITE))
                   .build());
        }
        for (Team team : Team.values()) {
            int alive = plugin.getAlivePlayers(team).size();
            int score = plugin.save.teams.get(team).score;
            ls.add(Component.text()
                   .append(Component.text(team.displayName, team.color, TextDecoration.BOLD))
                   .append(Component.space())
                   .append(Component.text("" + score, NamedTextColor.WHITE))
                   .append(Component.text("/", NamedTextColor.GRAY))
                   .append(Component.text("" + plugin.WINNING_SCORE, NamedTextColor.GRAY))
                   .build());
            ls.add(Component.text()
                   .append(Component.space())
                   .append(Component.text("Alive ", NamedTextColor.GRAY))
                   .append(Component.text("" + alive, NamedTextColor.WHITE))
                   .build());
        }
        event.add(plugin, Priority.HIGHEST, ls);
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
        switch (event.getCause()) {
        case VOID:
            event.setCancelled(true);
            event.getEntity().teleport(plugin.world.getSpawnLocation());
            break;
        case ENTITY_ATTACK:
        case ENTITY_SWEEP_ATTACK:
            event.setDamage(0);
            break;
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (plugin.save.state != State.PLAY) {
            event.setCancelled(true);
            return;
        }
        Vec3i vec = Vec3i.of(event.getBlock());
        if (!plugin.gameRegion.contains(vec)) {
            event.setCancelled(true);
            return;
        }
        for (Team team : Team.values()) {
            if (Objects.equals(plugin.teamInfos.get(team).treasure, vec)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (plugin.save.state != State.PLAY) {
            event.setCancelled(true);
            return;
        }
        for (BlockState state : event.getReplacedBlockStates()) {
            Vec3i vec = Vec3i.of(state.getBlock());
            for (Team team : Team.values()) {
                if (Objects.equals(plugin.teamInfos.get(team).treasure, vec)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
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
        Player player = event.getPlayer();
        if (plugin.save.state != State.PLAY) {
            event.setCancelled(true);
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) return;
        Block block = event.getBlock();
        Vec3i vec = Vec3i.of(block);
        if (!plugin.gameRegion.contains(vec)) {
            event.setCancelled(true);
            return;
        }
        if (!onBlockBreak(block, player)) event.setCancelled(true);
    }

    boolean onBlockBreak(Block block, Player breaker) {
        Vec3i vec = Vec3i.of(block);
        for (Map.Entry<UUID, Pirate> entry : new ArrayList<>(plugin.save.players.entrySet())) {
            Pirate bedPirate = entry.getValue();
            if (Objects.equals(bedPirate.bed1, vec) || Objects.equals(bedPirate.bed2, vec)) {
                bedPirate.bed1 = null;
                bedPirate.bed2 = null;
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    player.sendMessage(Component.text("Your bed was broken!", NamedTextColor.RED));
                }
            }
        }
        Team stolenTeam = null;
        for (Team team : Team.values()) {
            if (block.getType() == plugin.TREASURE_MAT && Objects.equals(plugin.teamInfos.get(team).treasure, vec)) {
                stolenTeam = team;
                break;
            }
        }
        if (stolenTeam != null) {
            TeamSave teamSave = plugin.save.teams.get(stolenTeam);
            teamSave.treasureRespawnCooldown = 20 * 60;
            ArmorStand armorStand = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ArmorStand.class, e -> {
                    e.setPersistent(false);
                    e.setSmall(true);
                    e.setMarker(true);
                    e.setInvisible(true);
                    e.setCustomNameVisible(true);
                    e.customName(Component.text("" +  (teamSave.treasureRespawnCooldown / 20), NamedTextColor.YELLOW));
                });
            if (armorStand != null) teamSave.respawnArmorStand = armorStand.getUniqueId();
            Component message = breaker != null
                ? Component.text(breaker.getName() + " stole team " + stolenTeam.displayName + "'s treasure!",
                                 stolenTeam.color)
                : Component.text("Team " + stolenTeam.displayName + " had their treasure stolen!",
                                 stolenTeam.color);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(message);
            }
            if (breaker != null) {
                Pirate breakPirate = plugin.save.getPirate(breaker);
                if (breakPirate != null) {
                    if (breakPirate.team != stolenTeam) {
                        breakPirate.score += 1;
                        if (breakPirate.score > plugin.save.highestScore) {
                            plugin.save.highestScore = breakPirate.score;
                            plugin.save.highestScorePlayer = breaker.getUniqueId();
                            plugin.save.highestScoreTeam = breakPirate.team;
                        }
                        plugin.save.teams.get(breakPirate.team).score += 1;
                    } else {
                        breakPirate.score = Math.max(0, breakPirate.score - 1);
                        plugin.save.teams.get(breakPirate.team).score = Math.max(0, plugin.save.teams.get(breakPirate.team).score - 1);
                    }
                }
                return true;
            }
        }
        return true;
    }

    @EventHandler
    void onBlockPreDispense(BlockPreDispenseEvent event) {
        if (plugin.save.state != State.PLAY) return;
        Block block = event.getBlock();
        BlockData bd = block.getBlockData();
        if (!(bd instanceof Dispenser)) return;
        Vec3i vec = Vec3i.of(block);
        if (!plugin.gameRegion.contains(vec)) return;
        ItemStack itemStack = event.getItemStack();
        if (itemStack.getType() != Material.TNT) return;
        event.setCancelled(true);
        if (plugin.save.cannonAt(vec) != null) return;
        itemStack.subtract(1);
        Dispenser dispenser = (Dispenser) bd;
        Location loc = vec.toBlock(plugin.world).getLocation()
            .add(0.5, 0.5, 0.5)
            .add(dispenser.getFacing().getOppositeFace().getDirection());
        int countdown = 20 * 3;
        ArmorStand armorStand = plugin.world.spawn(loc, ArmorStand.class, e -> {
                e.setPersistent(false);
                e.setSmall(true);
                e.setMarker(true);
                e.setInvisible(true);
                e.setCustomNameVisible(true);
                e.customName(Component.text("" + (countdown / 20), NamedTextColor.YELLOW));
            });
        Cannon cannon = new Cannon(vec, countdown, armorStand == null ? null : armorStand.getUniqueId());
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
                if (!onBlockBreak(block, null)) iter.remove();
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
                if (!onBlockBreak(block, null)) iter.remove();
            }
        }
    }

    @EventHandler
    void onBlockFromTo(BlockFromToEvent event) {
        if (event.getBlock().getType() == Material.FIRE) {
            if (plugin.random.nextInt(2) > 0) {
                event.setCancelled(true);
            }
        }
    }
}

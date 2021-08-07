package com.cavetale.overboard;

import com.cavetale.mytems.Mytems;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class OverboardPlugin extends JavaPlugin {
    protected OverboardCommand overboardCommand = new OverboardCommand(this);
    protected EventListener eventListener = new EventListener(this);
    protected Save save;
    protected World world;
    protected Map<Team, TeamInfo> teamInfos = new EnumMap<>(Team.class);
    Cuboid gameRegion;

    @Override
    public void onEnable() {
        overboardCommand.enable();
        eventListener.enable();
        getDataFolder().mkdirs();
        load();
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        loadWorld();
    }

    @Override
    public void onDisable() {
        save();
    }

    void loadWorld() {
        world = Bukkit.getWorlds().get(0);
        File areaFolder = new File(world.getWorldFolder(), "areas");
        AreaFile areaFile = Json.load(new File(areaFolder, "Overboard.json"), AreaFile.class, () -> null);
        if (areaFile == null) {
            getLogger().warning("Areas file not found!");
        } else {
            List<Cuboid> list;
            list = areaFile.areas.get("ships");
            Team[] teams = Team.values();
            if (list == null || list.size() != teams.length) {
                getLogger().warning("Areas: ships: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).ship = list.get(i);
                }
            }
            list = areaFile.areas.get("spawns");
            if (list == null || list.size() != 2) {
                getLogger().warning("Areas: spawns: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).spawn = list.get(i);
                }
            }
            list = areaFile.areas.get("game");
            if (list == null || list.size() != 1) {
                getLogger().warning("Areas: game: " + list);
            } else {
                gameRegion = list.get(0);
            }
        }
    }

    void load() {
        save = Json.load(new File(getDataFolder(), "save.json"), Save.class, Save::new);
    }

    void save() {
        Json.save(new File(getDataFolder(), "save.json"), save, true);
    }

    void tick() {
        if (save == null || save.state == null || save.state == State.IDLE) return;
        List<Player> alivePlayers = getAlivePlayers();
        if (save.state == State.WARMUP) {
            if (save.ticks >= 200) {
                save.state = State.PLAY;
                world.setPVP(true);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle("", "" + ChatColor.GREEN + "Fight!");
                    player.sendMessage("" + ChatColor.GREEN + "Fight!");
                }
            } else {
                if (save.ticks % 20 == 0) {
                    int seconds = (save.ticks - 1) / 20 + 1;
                    seconds = 10 - seconds;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle("" + ChatColor.GREEN + seconds,
                                         "" + ChatColor.GREEN + "Get Ready!");
                        player.sendMessage("" + ChatColor.GREEN + seconds + " Get Ready!");
                    }
                }
            }
        } else if (save.state == State.PLAY) {
            for (TeamInfo teamInfo : teamInfos.values()) teamInfo.alive = 0;
            for (Player player : alivePlayers) {
                Pirate pirate = save.getPirate(player);
                teamInfos.get(pirate.team).alive += 1;
            }
            Team winningTeam = null;
            int aliveTeams = 0;
            for (Team team : Team.values()) {
                TeamInfo teamInfo = teamInfos.get(team);
                if (teamInfo.alive > 0) {
                    aliveTeams += 1;
                }
                if (aliveTeams == 1) {
                    winningTeam = team;
                } else {
                    winningTeam = null;
                    break;
                }
            }
            if (winningTeam != null) {
                // Bukkit.getScheduler().runTaskLater(this, () -> {
                //         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winner.getName() + " Blackbeard");
                //     }, 40);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(Component.text(winningTeam.displayName, winningTeam.color),
                                                 Component.text("wins the game!", winningTeam.color)));
                    player.sendMessage(Component.text(winningTeam.displayName + " wins the game!", winningTeam.color));
                }
                getLogger().info(winningTeam.displayName + " wins this game!");
                stopGame();
                return;
            } else if (aliveTeams == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(Component.text("Draw!", NamedTextColor.RED),
                                                 Component.text("Everyone is dead", NamedTextColor.RED)));
                    player.sendMessage(Component.text("Draw! Everyone is dead", NamedTextColor.RED));
                }
                stopGame();
                return;
            }
            for (Player alive : alivePlayers) {
                if (alive.getLocation().getBlock().isLiquid() || !gameRegion.contains(alive.getLocation())) {
                    die(alive);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(ChatColor.AQUA + alive.getName() + " drowned!");
                    }
                }
            }
            for (Player player : world.getPlayers()) {
                Pirate pirate = save.getPirate(player);
                if (pirate == null) continue;
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (pirate.bed1 == null) {
                        save.players.remove(player.getUniqueId());
                        continue;
                    }
                    if (pirate.respawnCooldown > 0) {
                        pirate.respawnCooldown -= 1;
                        if (pirate.respawnCooldown > 0 && (pirate.respawnCooldown % 20) == 0) {
                            int seconds = pirate.respawnCooldown / 20;
                            player.showTitle(Title.title(Component.empty(),
                                                         Component.text("" + seconds, NamedTextColor.WHITE),
                                                         Title.Times.of(Duration.ZERO,
                                                                        Duration.ofMillis(500),
                                                                        Duration.ZERO)));
                        }
                    } else {
                        spawnPlayer(player, pirate, pirate.bed1.toBlock(world).getLocation().add(0.5, 0.5, 0.5));
                    }
                }
            }
            Iterator<Cannon> iter = save.cannons.iterator();
            while (iter.hasNext()) {
                Cannon cannon = iter.next();
                Entity e = Bukkit.getEntity(cannon.armorStand);
                ArmorStand armorStand = e instanceof ArmorStand
                    ? (ArmorStand) e
                    : null;
                Block block = cannon.vector.toBlock(world);
                BlockData bd = block.getBlockData();
                if (bd.getMaterial() != Material.DISPENSER) {
                    if (armorStand != null) armorStand.remove();
                    iter.remove();
                } else if (cannon.countdown > 0) {
                    cannon.countdown -= 1;
                    if (armorStand != null) {
                        armorStand.customName(Component.text("" + (cannon.countdown / 20), NamedTextColor.YELLOW));
                    }
                } else {
                    if (armorStand != null) armorStand.remove();
                    iter.remove();
                    Dispenser dispenser = (Dispenser) bd;
                    Location loc = block.getRelative(dispenser.getFacing()).getLocation().add(0.5, 0.5, 0.5);
                    Vector velocity = dispenser.getFacing().getDirection().multiply(2.0);
                    loc.getWorld().spawn(loc, TNTPrimed.class, t -> {
                            t.setVelocity(velocity);
                            t.setFuseTicks(30);
                        });
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0f, 0.8f);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 2, 0.5, 0.5, 0.5, 0.0);
                }
            }
        }
        save.ticks += 1;
    }

    protected void die(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        Pirate pirate = save.getPirate(player);
        if (pirate == null) return;
        pirate.respawnCooldown = 200;
    }

    List<Player> getAlivePlayers() {
        List<Player> result = new ArrayList<>();
        if (save.players == null) return result;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid()) continue;
            switch (player.getGameMode()) {
            case SURVIVAL: case ADVENTURE: break; // OK
            default: continue; // NO
            }
            if (save.players.containsKey(player.getUniqueId())) {
                result.add(player);
            }
        }
        return result;
    }

    void startGame() {
        save.state = State.WARMUP;
        save.ticks = 0;
        world.setPVP(false);
        save.players.clear();
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE) continue;
            if (player.isPermissionSet("group.streamer") && player.hasPermission("group.streamer")) continue;
            players.add(player);
        }
        Collections.shuffle(players);
        int half = players.size() / 2;
        for (int i = 0; i < players.size(); i += 1) {
            Player player = players.get(i);
            Team team = i < half ? Team.RED : Team.BLUE;
            Pirate pirate = new Pirate(team);
            save.players.put(player.getUniqueId(), pirate);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            Location spawnLocation = teamInfos.get(team).spawn.random()
                .toBlock(world).getLocation()
                .add(0.5, 0.0, 0.5);
            spawnPlayer(player, pirate, spawnLocation);
            player.getInventory().setItem(0, Mytems.CAPTAINS_CUTLASS.createItemStack());
            player.getInventory().setItem(1, Mytems.BLUNDERBUSS.createItemStack());
            player.getInventory().setItem(8, new ItemStack(Material.APPLE, 12));
        }
        save();
    }

    void spawnPlayer(Player player, Pirate pirate, Location location) {
        player.teleport(location);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setHelmet(Mytems.PIRATE_HAT.createItemStack());
    }

    void stopGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            player.setGameMode(GameMode.SPECTATOR);
        }
        for (Cannon cannon : save.cannons) {
            Entity entity = Bukkit.getEntity(cannon.armorStand);
            if (entity != null) entity.remove();
        }
        save.cannons.clear();
        save.players.clear();
        save.state = State.IDLE;
        save();
        world.setPVP(false);
    }
}

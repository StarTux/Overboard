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
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
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
    public static final int WINNING_SCORE = 5;
    protected OverboardCommand overboardCommand = new OverboardCommand(this);
    protected EventListener eventListener = new EventListener(this);
    protected Save save;
    protected World world;
    protected Map<Team, TeamInfo> teamInfos = new EnumMap<>(Team.class);
    protected Random random;
    Cuboid gameRegion;

    @Override
    public void onEnable() {
        random = ThreadLocalRandom.current();
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
        Team[] teams = Team.values();
        for (Team team : teams) {
            teamInfos.put(team, new TeamInfo());
        }
        if (areaFile == null) {
            getLogger().warning("Areas file not found!");
        } else {
            List<Cuboid> list;
            list = areaFile.areas.get("ships");
            if (list == null || list.size() != teams.length) {
                getLogger().warning("Areas: ships: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).ship = list.get(i);
                }
            }
            list = areaFile.areas.get("spawns");
            if (list == null || list.size() != teams.length) {
                getLogger().warning("Areas: spawns: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).spawn = list.get(i);
                }
            }
            list = areaFile.areas.get("treasures");
            if (list == null || list.size() != teams.length) {
                getLogger().warning("Areas: treasures: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).treasure = list.get(i).min;
                }
            }
            list = areaFile.areas.get("explosives");
            if (list == null || list.size() != teams.length) {
                getLogger().warning("Areas: explosives: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).explosive = list.get(i);
                }
            }
            list = areaFile.areas.get("drops");
            if (list == null || list.size() != teams.length) {
                getLogger().warning("Areas: drops: " + list);
            } else {
                for (int i = 0; i < teams.length; i += 1) {
                    teamInfos.get(teams[i]).drop = list.get(i);
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
                    if (aliveTeams == 1) {
                        winningTeam = team;
                    } else {
                        winningTeam = null;
                        break;
                    }
                }
            }
            if (winningTeam == null) {
                for (Team team : Team.values()) {
                    if (save.teams.get(team).score >= WINNING_SCORE) {
                        winningTeam = team;
                        break;
                    }
                }
            }
            if (winningTeam != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(Component.text(winningTeam.displayName, winningTeam.color),
                                                 Component.text("wins the game!", winningTeam.color)));
                    player.sendMessage(Component.text(winningTeam.displayName + " wins the game!", winningTeam.color));
                }
                getLogger().info(winningTeam.displayName + " wins this game!");
                if (save.highestScorePlayer != null && save.highestScoreTeam == winningTeam) {
                    Player highestScorePlayer = Bukkit.getPlayer(save.highestScorePlayer);
                    if (highestScorePlayer != null) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.sendMessage(Component.text(highestScorePlayer.getName() + " has the highest score: " + save.highestScore,
                                                              winningTeam.color));
                        }
                        if (!save.debug) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + highestScorePlayer.getName() + " Blackbeard");
                        }
                    }
                }
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
                        player.sendMessage(Component.text("Your bed spawn is missing. You are out of the game!",
                                                          NamedTextColor.RED));
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
            // Cannons
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
                    Vector velocity = dispenser.getFacing().getDirection();
                    if (Math.abs(velocity.getY()) < 0.01) velocity.setY(0.25);
                    velocity.multiply(3.0);
                    loc.getWorld().spawn(loc, TNTPrimed.class, t -> {
                            t.setVelocity(velocity);
                            t.setFuseTicks(20);
                        });
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0f, 0.8f);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 2, 0.5, 0.5, 0.5, 0.0);
                }
            }
            // Treasure
            for (Team team : Team.values()) {
                Block block = teamInfos.get(team).treasure.toBlock(world);
                if (block.getType() != Material.AMETHYST_BLOCK) {
                    TeamSave teamSave = save.teams.get(team);
                    if (teamSave.treasureRespawnCooldown > 0) {
                        teamSave.treasureRespawnCooldown -= 1;
                        if (teamSave.respawnArmorStand != null) {
                            Entity entity = Bukkit.getEntity(teamSave.respawnArmorStand);
                            if (entity != null) {
                                entity.customName(Component.text("" + (teamSave.treasureRespawnCooldown / 20), NamedTextColor.YELLOW));
                            }
                        }
                    } else {
                        block.setType(Material.AMETHYST_BLOCK);
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.sendMessage(Component.text("The treasure of team " + team.displayName + " respawned!",
                                                              team.color));
                        }
                        if (teamSave.respawnArmorStand != null) {
                            Entity entity = Bukkit.getEntity(teamSave.respawnArmorStand);
                            if (entity != null) entity.remove();
                            teamSave.respawnArmorStand = null;
                        }
                    }
                }
            }
            // Explosives
            if (save.explosiveCooldown > 0) {
                save.explosiveCooldown -= 1;
            } else {
                save.explosiveCooldown = 20 * (5 + random.nextInt(10));
                for (Team team : Team.values()) {
                    Block block = teamInfos.get(team).explosive.random().toBlock(world);
                    if (block.isEmpty()) block.setType(Material.TNT);
                }
            }
            // Drop
            if (save.dropCooldown > 0) {
                save.dropCooldown -= 1;
            } else {
                save.dropCooldown = 20 * (5 + random.nextInt(10));
                for (Team team : Team.values()) {
                    Block block = teamInfos.get(team).drop.random().toBlock(world);
                    Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                    ItemStack item;
                    switch (random.nextInt(6)) {
                    case 0: item = new ItemStack(Material.APPLE, 8); break;
                    case 1: item = new ItemStack(Material.ENDER_PEARL, 2); break;
                    case 2: item = new ItemStack(Material.FLINT_AND_STEEL); break;
                    case 3: item = new ItemStack(Material.TNT); break;
                    case 4: item = new ItemStack(Material.BUCKET); break;
                    case 5: item = new ItemStack(Material.LAVA_BUCKET); break;
                    default: item = null; break;
                    }
                    if (item != null) {
                        world.dropItemNaturally(loc, item);
                    }
                }
            }
        }
        save.ticks += 1;
    }

    protected void die(Player player) {
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

    List<Player> getAlivePlayers(Team team) {
        List<Player> result = new ArrayList<>();
        if (save.players == null) return result;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid()) continue;
            switch (player.getGameMode()) {
            case SURVIVAL: case ADVENTURE: break; // OK
            default: continue; // NO
            }
            Pirate pirate = save.getPirate(player);
            if (pirate == null) continue;
            if (pirate.team != team) continue;
            result.add(player);
        }
        return result;
    }

    void startGame() {
        save.newGame();
        world.setPVP(false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
        world.setGameRule(GameRule.DO_TILE_DROPS, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        for (Team team : Team.values()) {
            save.teams.put(team, new TeamSave());
        }
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
            if (!save.debug) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            }
            Location spawnLocation = teamInfos.get(team).spawn.random()
                .toBlock(world).getLocation()
                .add(0.5, 0.0, 0.5);
            spawnPlayer(player, pirate, spawnLocation);
            player.getInventory().clear();
            player.getInventory().setItem(0, Mytems.CAPTAINS_CUTLASS.createItemStack());
            player.getInventory().setItem(1, Mytems.BLUNDERBUSS.createItemStack());
            player.getInventory().setItem(2, new ItemStack(Material.BLACK_BED));
            player.getInventory().setItem(8, new ItemStack(Material.APPLE, 12));
            player.getInventory().setHelmet(Mytems.PIRATE_HAT.createItemStack());
        }
        save();
    }

    void spawnPlayer(Player player, Pirate pirate, Location location) {
        player.teleport(location);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setGameMode(GameMode.SURVIVAL);
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
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
    }
}

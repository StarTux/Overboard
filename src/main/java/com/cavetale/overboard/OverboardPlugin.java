package com.cavetale.overboard;

import com.cavetale.area.struct.Area;
import com.cavetale.area.struct.AreasFile;
import com.cavetale.core.money.Money;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
import com.winthier.title.TitlePlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import static com.cavetale.mytems.util.Items.tooltip;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.title.Title.title;

public final class OverboardPlugin extends JavaPlugin {
    private static OverboardPlugin instance;
    private static final int FLOOD_COOLDOWN = 20 * 60;
    protected final OverboardCommand overboardCommand = new OverboardCommand(this);
    protected final EventListener eventListener = new EventListener(this);
    protected final Random random = ThreadLocalRandom.current();
    // Save
    protected Save save;
    // World
    protected BuildWorld buildWorld;
    protected World world;
    protected List<Area> gameAreas;
    protected List<Area> deathAreas;
    protected List<Area> spawnAreas;
    protected List<Area> dropAreas;
    protected List<Highscore> highscores;
    protected List<Component> highscoreLines;
    protected static final Component TITLE = textOfChildren(Mytems.CAPTAINS_CUTLASS, text("Overboard!", DARK_RED));
    protected String timeString;
    protected int playerCount;
    // Spawn
    private List<Vec3i> spawnLocations = List.of();
    private int nextSpawnLocationIndex = 0;

    public OverboardPlugin() {
        instance = this;
    }

    public static OverboardPlugin plugin() {
        return instance;
    }

    @Override
    public void onEnable() {
        overboardCommand.enable();
        eventListener.enable();
        getDataFolder().mkdirs();
        load();
        save.state = State.IDLE;
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        computeHighscores();
    }

    @Override
    public void onDisable() {
        save();
        if (world != null) {
            Files.deleteWorld(world);
            world = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            TitlePlugin.getInstance().setColor(player, null);
        }
    }


    public boolean isGameWorld(World w) {
        return w.equals(this.world);
    }

    public boolean isGameArea(Vec3i vec) {
        for (Area area : gameAreas) {
            if (area.contains(vec)) return true;
        }
        return false;
    }

    public boolean isDeathArea(Vec3i vec) {
        for (Area area : deathAreas) {
            if (area.contains(vec)) return true;
        }
        return false;
    }

    public void load() {
        save = Json.load(new File(getDataFolder(), "save.json"), Save.class, Save::new);
    }

    public void save() {
        Json.save(new File(getDataFolder(), "save.json"), save, true);
    }

    public void startGame(BuildWorld theBuildWorld) {
        this.buildWorld = theBuildWorld;
        buildWorld.makeLocalCopyAsync(this::onWorldLoaded);
    }

    private void onWorldLoaded(World newWorld) {
        this.world = newWorld;
        // WOrld
        world.setPVP(false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setDifficulty(Difficulty.PEACEFUL);
        // Area
        AreasFile areasFile = AreasFile.load(world, "Overboard");
        try {
            if (areasFile == null) throw new IllegalStateException("Loaded areas file is null");
            gameAreas = areasFile.find("game");
            if (gameAreas.isEmpty()) throw new IllegalStateException("Game areas is emtpy");
            deathAreas = areasFile.find("death");
            spawnAreas = areasFile.find("spawn");
            if (spawnAreas.isEmpty()) throw new IllegalStateException("Spawn areas is emtpy");
            dropAreas = areasFile.find("drop");
            if (dropAreas.isEmpty()) throw new IllegalStateException("Drop areas is emtpy");
        } catch (IllegalStateException iae) {
            iae.printStackTrace();
            world = null;
        }
        // Spawns
        spawnLocations = findSpawnLocations();
        Collections.shuffle(spawnLocations);
        //
        save.waterLevel = world.getMinHeight();
        for (var gameArea : gameAreas) {
            for (var vec : gameArea.enumerate()) {
                final var block = vec.toBlock(world);
                if (block.getType() == Material.WATER) {
                    if (save.waterLevel < block.getY()) {
                        save.waterLevel = block.getY();
                    }
                }
            }
        }
        // Players
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            if (player.isPermissionSet("group.streamer") && player.hasPermission("group.streamer")) continue;
            players.add(player);
        }
        Collections.shuffle(players);
        save.pirates.clear();
        for (Player player : players) {
            startPlayer(player);
        }
        save.state = State.WARMUP;
        save.winners = List.of();
        save.winnerTeam = null;
        save.ticks = 0;
        save.warmupTicks = 0;
        save.gameTicks = 0;
        save.endTicks = 0;
        save.tickSpeed = 0;
        save.floodCooldown = FLOOD_COOLDOWN;
        save.nextFloodCooldown = FLOOD_COOLDOWN - 20;
    }

    /**
     * Start a player unless they're already playing.
     * Do everything except pick and port to a spawn location.
     */
    public void startPlayer(Player player) {
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate != null && pirate.playing) {
            if (save.useTeams && pirate.team != null) {
                TitlePlugin.getInstance().setColor(player, pirate.team.color);
            }
            return;
        }
        pirate = new Pirate();
        pirate.uuid = player.getUniqueId();
        pirate.name = player.getName();
        pirate.playing = true;
        if (save.useTeams) {
            int red = 0;
            int blue = 0;
            for (Pirate other : save.pirates.values()) {
                if (other.team == PirateTeam.RED) {
                    red += 1;
                } else if (other.team == PirateTeam.BLUE) {
                    blue += 1;
                }
            }
            pirate.team = red < blue ? PirateTeam.RED : PirateTeam.BLUE;
            TitlePlugin.getInstance().setColor(player, pirate.team.color);
        }
        save.pirates.put(player.getUniqueId(), pirate);
        player.getInventory().clear();
        player.getInventory().setItem(0, Mytems.CAPTAINS_CUTLASS.createItemStack());
        player.getInventory().setItem(1, Mytems.BLUNDERBUSS.createItemStack());
        player.getInventory().setItem(8, new ItemStack(Material.APPLE, 12));
        player.getInventory().setHelmet(Mytems.PIRATE_HAT.createItemStack());
        if (save.useTeams) {
            player.getInventory().setChestplate(makeColoredArmor(Material.LEATHER_CHESTPLATE, pirate.team));
            player.getInventory().setLeggings(makeColoredArmor(Material.LEATHER_LEGGINGS, pirate.team));
            player.getInventory().setBoots(makeColoredArmor(Material.LEATHER_BOOTS, pirate.team));
        }
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setGameMode(GameMode.SURVIVAL);
        pirate.spawnLocation = spawnLocations.get(nextSpawnLocationIndex++ % spawnLocations.size());
        player.teleport(pirate.spawnLocation.toCenterFloorLocation(world));
        if (save.event) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
        }
    }

    private ItemStack makeColoredArmor(Material material, PirateTeam team) {
        ItemStack item = new ItemStack(material);
        item.editMeta(m -> {
                if (m instanceof LeatherArmorMeta meta) {
                    meta.setColor(Color.fromRGB(team.color.value() & 0xFFFFFF));
                    meta.setUnbreakable(true);
                    meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
                }
            });
        return item;
    }

    protected boolean respawnPlayer(Player player) {
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate == null) return false;
        List<Vec3i> respawnLocations = findSpawnLocations();
        if (respawnLocations.isEmpty()) return false;
        pirate.spawnLocation = respawnLocations.get(random.nextInt(respawnLocations.size()));
        player.teleport(pirate.spawnLocation.toCenterFloorLocation(world));
        player.setHealth(20.0);
        player.setFireTicks(0);
        player.setGameMode(GameMode.SURVIVAL);
        pirate.dead = false;
        if (save.useTeams && pirate.team != null) {
            TitlePlugin.getInstance().setColor(player, pirate.team.color);
        }
        return true;
    }

    protected boolean respawnPlayer(Player player, Location location) {
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate == null) return false;
        player.teleport(location);
        player.setHealth(20.0);
        player.setFireTicks(0);
        player.setGameMode(GameMode.SURVIVAL);
        pirate.dead = false;
        if (save.useTeams && pirate.team != null) {
            TitlePlugin.getInstance().setColor(player, pirate.team.color);
        }
        return true;
    }

    public void stopGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
            TitlePlugin.getInstance().setColor(player, null);
        }
        save.pirates.clear();
        save.state = State.IDLE;
        save();
        World oldWorld = world;
        world = null;
        Files.deleteWorld(oldWorld);
    }

    protected List<Vec3i> findSpawnLocations() {
        List<Vec3i> result = new ArrayList<>();
        for (Area area : spawnAreas) {
            for (Vec3i vec : area.enumerate()) {
                Block block = vec.toBlock(world);
                if (block.isLiquid()) continue;
                if (block.getRelative(0, 1, 0).isLiquid()) continue;
                if (!block.getCollisionShape().getBoundingBoxes().isEmpty()) continue;
                if (!block.getRelative(0, 1, 0).getCollisionShape().getBoundingBoxes().isEmpty()) continue;
                Collection<BoundingBox> bbs = block.getRelative(0, -1, 0).getCollisionShape().getBoundingBoxes();
                if (bbs.size() != 1) continue;
                BoundingBox bb = bbs.iterator().next();
                if (bb.getHeight() == 1.0 && bb.getWidthX() == 1.0 && bb.getWidthZ() == 1.0) {
                    result.add(vec);
                }
            }
        }
        return result;
    }

    private void tick() {
        if (world == null || save == null || save.state == null || save.state == State.IDLE) return;
        if (save.state == State.WARMUP) {
            tickWarmup();
        } else if (save.state == State.GAME) {
            tickGame();
        } else if (save.state == State.END) {
            tickEnd();
        }
        save.ticks += 1;
    }

    protected static final int WARMUP_TICKS = 20 * 60;

    private void tickWarmup() {
        if (save.warmupTicks >= WARMUP_TICKS) {
            save.state = State.GAME;
            world.setPVP(true);
            world.setDifficulty(Difficulty.HARD);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title(empty(), textOfChildren(Mytems.CAPTAINS_CUTLASS, text(" Fight!", DARK_RED))));
                player.sendMessage(empty());
                player.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, text(" Fight!", DARK_RED)));
                player.sendMessage(empty());
                player.sendActionBar(textOfChildren(Mytems.CAPTAINS_CUTLASS, text(" Fight!", DARK_RED)));
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.5f, 1.0f);
            }
            return;
        }
        // Show Countdown
        if (save.warmupTicks % 20 == 0) {
            final int seconds = (WARMUP_TICKS - save.warmupTicks - 1) / 20 + 1;
            timeString = "" + seconds;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(textOfChildren(text(seconds, GRAY), text(" Get Ready!", GREEN)));
                float progress = ((float) save.warmupTicks / (float) WARMUP_TICKS) * 1.5f + 0.5f;
                player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.MASTER, 0.5f, progress);
            }
        }
        // Anchor to spawn
        for (Player player : getAlivePlayers()) {
            Location loc = player.getLocation();
            Location spawn = save.pirates.get(player.getUniqueId()).spawnLocation.toCenterFloorLocation(world);
            if (!loc.getWorld().equals(spawn.getWorld()) || loc.distanceSquared(spawn) > 1.0) {
                spawn.setYaw(loc.getYaw());
                spawn.setPitch(loc.getPitch());
                player.teleport(spawn);
            }
        }
        save.warmupTicks += 1;
    }

    private void tickGame() {
        for (Player player : world.getPlayers()) {
            Pirate pirate = save.pirates.get(player.getUniqueId());
            if (pirate == null || !pirate.playing) continue;
            tickGamePlayer(player, pirate);
        }
        List<Player> alive = getAlivePlayers();
        playerCount = alive.size();
        if (!save.debug && save.useTeams) {
            Map<PirateTeam, Integer> aliveCounts = countAliveTeams();
            if (aliveCounts.size() == 1) {
                save.state = State.END;
                save.winnerTeam = aliveCounts.keySet().iterator().next();
                List<Pirate> winnerPirates = getTeamPirates(save.winnerTeam);
                save.winners = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (Pirate pirate : winnerPirates) {
                    save.winners.add(pirate.uuid);
                    names.add(pirate.name);
                    if (save.event) {
                        save.addScore(pirate.uuid, 3);
                        pirate.money += 5000;
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + pirate.name + " Blackbeard Scalawag DavyJones");
                    }
                }
                if (save.event) computeHighscores();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(empty());
                    online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, space(), text("Team ", WHITE), save.winnerTeam.component,
                                                      text(" wins the game!", WHITE)));
                    online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, space(), text(String.join(" ", names))));
                    online.sendMessage(empty());
                    online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
                }
                if (save.event) giveMoney();
                return;
            }
        }
        if (!save.debug && !save.useTeams && alive.size() == 1) {
            save.state = State.END;
            final Player winner = alive.get(0);
            final Pirate pirate = save.pirates.get(winner.getUniqueId());
            save.winners = List.of(winner.getUniqueId());
            if (save.event) {
                save.addScore(winner.getUniqueId(), 3);
                if (pirate != null) pirate.money += 5000;
                computeHighscores();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winner.getName() + " Blackbeard Scalawag DavyJones");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(empty());
                online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, space(), winner.displayName(), text(" wins the game!", GREEN)));
                online.sendMessage(empty());
                online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
            }
            if (save.event) giveMoney();
            return;
        } else if (!save.debug && alive.isEmpty()) {
            save.state = State.END;
            save.winners = List.of();
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(empty());
                online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, text(" The game ends in a draw!", DARK_RED)));
                online.sendMessage(empty());
                online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
            }
            if (save.event) giveMoney();
            return;
        }
        if (save.dropCooldown <= 0) {
            drop();
            save.dropCooldown = 20;
            if (save.gameTicks > 20 * 60 && random.nextInt(10) < save.gameTicks / (20 * 60)) {
                dropPlayer();
            }
        } else {
            save.dropCooldown -= 1;
        }
        if (save.gameTicks % 600 == 0) {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, save.tickSpeed);
            save.tickSpeed += 1;
        }
        int seconds = save.gameTicks / 20;
        int minutes = seconds / 60;
        timeString = minutes + "m " + (seconds % 60) + "s";
        // Flood
        if (save.floodCooldown > 0) {
            save.floodCooldown -= 1;
        } else {
            save.floodCooldown = save.nextFloodCooldown;
            save.nextFloodCooldown -= 100;
            if (save.nextFloodCooldown < 100) {
                save.nextFloodCooldown = 100;
            }
            save.waterLevel += 1;
            for (var gameArea : gameAreas) {
                if (save.waterLevel >= gameArea.max.y) continue;
                for (int z = gameArea.min.z; z <= gameArea.max.z; z += 1) {
                    for (int x = gameArea.min.x; x <= gameArea.max.x; x += 1) {
                        final var block = world.getBlockAt(x, save.waterLevel, z);
                        if (block.getType() == Material.WATER) {
                            continue;
                        } else if (block.getType() == Material.LAVA) {
                            block.setBlockData(Material.WATER.createBlockData(), false);
                        } else if (block.isEmpty()) {
                            block.setBlockData(Material.WATER.createBlockData(), false);
                        } else if (block.getBlockData() instanceof Waterlogged waterlogged) {
                            waterlogged.setWaterlogged(true);
                            block.setBlockData(waterlogged, false);
                        } else if (block.getCollisionShape().getBoundingBoxes().isEmpty()) {
                            block.setBlockData(Material.WATER.createBlockData(), false);
                        }
                    }
                }
            }
            for (var player : world.getPlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
            }
        }
        // Explosion, see Eventlistener
        if (save.explosionCountdown > 0) {
            save.explosionCountdown -= 1;
        }
        // Game Ticks
        save.gameTicks += 1;
    }

    private void giveMoney() {
        if (!save.event) return;
        for (Pirate pirate : save.pirates.values()) {
            if (pirate.money == 0) continue;
            Money.get().give(pirate.uuid, (double) pirate.money, this, "Overboard!");
            pirate.money = 0;
        }
    }

    private static final List<ItemStack> DROP_ITEMS = List.of(new ItemStack(Material.FLINT_AND_STEEL),
                                                              new ItemStack(Material.TNT),
                                                              new ItemStack(Material.IRON_AXE),
                                                              new ItemStack(Material.SHEARS),
                                                              new ItemStack(Material.APPLE, 4),
                                                              new ItemStack(Material.APPLE, 8),
                                                              new ItemStack(Material.BREAD, 4),
                                                              new ItemStack(Material.BREAD, 8),
                                                              new ItemStack(Material.LAVA_BUCKET),
                                                              new ItemStack(Material.FIRE_CHARGE),
                                                              new ItemStack(Material.LADDER, 16),
                                                              new ItemStack(Material.ENDER_PEARL),
                                                              new ItemStack(Material.ENDER_PEARL, 2),
                                                              new ItemStack(Material.SPYGLASS));

    private static ItemStack totem() {
        return tooltip(new ItemStack(Material.TOTEM_OF_UNDYING),
                       List.of(text("Respawn Team Member", GREEN),
                               textOfChildren(Mytems.MOUSE_RIGHT, text(" activate", GRAY)),
                               text("Respawn a drowned team", GRAY),
                               text("member.", GRAY)));
    }

    private void drop() {
        List<Vec3i> vecs = new ArrayList<>();
        for (Area area : dropAreas) {
            for (Vec3i vec : area.enumerate()) {
                Block block = vec.toBlock(world);
                if (!block.getCollisionShape().getBoundingBoxes().isEmpty()) continue;
                if (block.getRelative(0, -1, 0).getCollisionShape().getBoundingBoxes().isEmpty()) continue;
                vecs.add(vec);
            }
        }
        if (vecs.isEmpty()) return;
        Vec3i vec = vecs.get(random.nextInt(vecs.size()));
        Location location = world.getBlockAt(vec.x, world.getMaxHeight(), vec.z).getLocation().add(0.5, 0.0, 0.5);
        switch (random.nextInt(30)) {
        case 0:
            // if (save.gameTicks < 20 * 60 * 5) return;
            // world.spawnEntity(location, EntityType.MINECART_TNT);
            // break;
        case 1:
            if (save.gameTicks < 20 * 60 * 4) return;
            world.strikeLightning(vec.toCenterFloorLocation(world));
            break;
        case 2:
        case 3:
            if (save.gameTicks < 20 * 60 * 2) return;
            world.spawn(location, FallingBlock.class, e -> e.setBlockData(Material.FIRE.createBlockData()));
            break;
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
            world.dropItem(location, totem());
            break;
        default:
            ItemStack item = DROP_ITEMS.get(random.nextInt(DROP_ITEMS.size()));
            world.dropItem(location, item.clone());
        }
    }

    private void dropPlayer() {
        List<Vec3i> vecs = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Pirate pirate = save.pirates.get(player.getUniqueId());
            if (pirate == null || !pirate.playing) continue;
            vecs.add(Vec3i.of(player.getLocation().getBlock()));
        }
        if (vecs.isEmpty()) return;
        Vec3i vec = vecs.get(random.nextInt(vecs.size()));
        Location location = world.getBlockAt(vec.x, world.getMaxHeight(), vec.z).getLocation().add(0.5, 0.0, 0.5);
        world.spawn(location, FallingBlock.class, e -> e.setBlockData(Material.FIRE.createBlockData()));
    }

    private void tickGamePlayer(Player player, Pirate pirate) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (pirate.respawnCooldown <= 0) {
                if (!respawnPlayer(player)) {
                    pirate.playing = false;
                    player.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, text(" You could not be respawned and are out of the game", RED)));
                    player.showTitle(title(text("Game Over", RED), empty()));
                }
                return;
            } else {
                pirate.respawnCooldown -= 1;
                if (pirate.respawnCooldown == 0) {
                    player.sendActionBar(empty());
                } else if (pirate.respawnCooldown > 0 && (pirate.respawnCooldown % 20) == 0) {
                    int seconds = pirate.respawnCooldown / 20;
                    player.sendActionBar(text("Respawn in " + seconds, GREEN));
                }
            }
            return;
        }
        // Death Checks
        if (player.getLocation().getBlock().getType() == Material.WATER) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.0f);
            die(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, space(), player.displayName(), text(" drowned in the ocean", RED)));
            }
            return;
        }
        Vec3i vec = Vec3i.of(player.getLocation().getBlock());
        if (!isGameArea(vec) || isDeathArea(vec)) {
            die(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, space(), player.displayName(), text(" got lost in the wilderness", RED)));
            }
            return;
        }
    }

    private void tickEnd() {
        if (save.endTicks > 20 * 60) {
            stopGame();
        } else {
            save.endTicks += 1;
        }
    }

    protected void die(Player player) {
        player.setFoodLevel(player.getFoodLevel() / 2);
        player.setGameMode(GameMode.SPECTATOR);
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate == null) return;
        pirate.dead = true;
        pirate.respawnCooldown = 100;
        for (int i = 0; i < pirate.deaths; i += 1) {
            pirate.respawnCooldown *= 2;
        }
        pirate.deaths += 1;
    }

    private List<Player> getAlivePlayers() {
        List<Player> result = new ArrayList<>();
        if (save.pirates == null) return result;
        for (Player player : world.getPlayers()) {
            if (player.isDead()) continue;
            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            Pirate pirate = save.pirates.get(player.getUniqueId());
            if (pirate == null || !pirate.playing) continue;
            result.add(player);
        }
        return result;
    }

    protected Map<PirateTeam, Integer> countAliveTeams() {
        Map<PirateTeam, Integer> result = new EnumMap<>(PirateTeam.class);
        for (Player p : getAlivePlayers()) {
            Pirate pirate = save.pirates.get(p.getUniqueId());
            if (pirate.team == null) continue;
            result.put(pirate.team, result.getOrDefault(pirate.team, 0) + 1);
        }
        return result;
    }

    protected List<Player> getTeamPlayers(PirateTeam team) {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Pirate pirate = save.pirates.get(p.getUniqueId());
            if (pirate == null || pirate.team != team) continue;
            result.add(p);
        }
        return result;
    }

    protected List<Pirate> getTeamPirates(PirateTeam team) {
        List<Pirate> result = new ArrayList<>();
        for (Pirate p : save.pirates.values()) {
            if (p.team != team) continue;
            result.add(p);
        }
        return result;
    }

    protected void computeHighscores() {
        highscores = Highscore.of(save.scores);
        highscoreLines = Highscore.sidebar(highscores, TrophyCategory.SWORD);
    }
}

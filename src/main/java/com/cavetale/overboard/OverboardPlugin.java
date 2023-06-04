package com.cavetale.overboard;

import com.cavetale.area.struct.Area;
import com.cavetale.area.struct.AreasFile;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.cavetale.overboard.world.Worlds;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.title.Title.title;

public final class OverboardPlugin extends JavaPlugin {
    private static OverboardPlugin instance;
    protected final OverboardCommand overboardCommand = new OverboardCommand(this);
    protected final EventListener eventListener = new EventListener(this);
    protected final Random random = ThreadLocalRandom.current();
    // Save
    protected Save save;
    // World
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
            Worlds.deleteWorld(world);
            world = null;
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

    protected void loadWorld() {
        world = Worlds.loadWorldCopy(save.worldName);
        if (world == null) throw new IllegalStateException("Loaded world is null");
        AreasFile areasFile = AreasFile.load(world, "Overboard");
        try {
            if (areasFile == null) throw new IllegalStateException("Loaded areas file is null");
            gameAreas = areasFile.find("game");
            if (gameAreas.isEmpty()) throw new IllegalStateException("Game areas is emtpy");
            deathAreas = areasFile.find("death");
            if (deathAreas.isEmpty()) throw new IllegalStateException("Death areas is emtpy");
            spawnAreas = areasFile.find("spawn");
            if (spawnAreas.isEmpty()) throw new IllegalStateException("Spawn areas is emtpy");
            dropAreas = areasFile.find("drop");
            if (dropAreas.isEmpty()) throw new IllegalStateException("Drop areas is emtpy");
        } catch (IllegalStateException iae) {
            iae.printStackTrace();
            Worlds.deleteWorld(world);
            world = null;
        }
    }

    public void load() {
        save = Json.load(new File(getDataFolder(), "save.json"), Save.class, Save::new);
    }

    public void save() {
        Json.save(new File(getDataFolder(), "save.json"), save, true);
    }

    public void startGame() {
        save.worldName = "StillDuck";
        loadWorld();
        world.setPVP(false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.DO_TILE_DROPS, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        // Spawns
        List<Vec3i> spawnLocations = findSpawnLocations();
        Collections.shuffle(spawnLocations);
        int nextSpawnLocationIndex = 0;
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
            Pirate pirate = save.pirates.get(player.getUniqueId());
            pirate.spawnLocation = spawnLocations.get(nextSpawnLocationIndex++ % spawnLocations.size());
            player.teleport(pirate.spawnLocation.toCenterFloorLocation(world));
            if (save.event) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            }
        }
        save.state = State.WARMUP;
        save.winner = null;
        save.ticks = 0;
        save.warmupTicks = 0;
        save.gameTicks = 0;
        save.endTicks = 0;
        save.tickSpeed = 6;
    }

    public void startPlayer(Player player) {
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate != null && pirate.playing) return;
        pirate = new Pirate();
        pirate.playing = true;
        save.pirates.put(player.getUniqueId(), pirate);
        player.getInventory().clear();
        player.getInventory().setItem(0, Mytems.CAPTAINS_CUTLASS.createItemStack());
        player.getInventory().setItem(1, Mytems.BLUNDERBUSS.createItemStack());
        player.getInventory().setItem(8, new ItemStack(Material.APPLE, 12));
        player.getInventory().setHelmet(Mytems.PIRATE_HAT.createItemStack());
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setGameMode(GameMode.SURVIVAL);
    }

    private boolean respawnPlayer(Player player) {
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate == null) return false;
        List<Vec3i> spawnLocations = findSpawnLocations();
        if (spawnLocations.isEmpty()) return false;
        pirate.spawnLocation = spawnLocations.get(random.nextInt(spawnLocations.size()));
        player.teleport(pirate.spawnLocation.toCenterFloorLocation(world));
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setGameMode(GameMode.SURVIVAL);
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
        }
        save.pirates.clear();
        save.state = State.IDLE;
        save();
        World oldWorld = world;
        world = null;
        Worlds.deleteWorld(oldWorld);
    }

    private List<Vec3i> findSpawnLocations() {
        List<Vec3i> result = new ArrayList<>();
        for (Area area : spawnAreas) {
            for (Vec3i vec : area.enumerate()) {
                Block block = vec.toBlock(world);
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

    private void tickWarmup() {
        final int totalTicks = 20 * 60;
        if (save.warmupTicks >= totalTicks) {
            save.state = State.GAME;
            world.setPVP(true);
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
            final int seconds = (totalTicks - save.warmupTicks - 1) / 20 + 1;
            timeString = "" + seconds;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(textOfChildren(text(seconds, GRAY), text(" Get Ready!", GREEN)));
                float progress = ((float) save.warmupTicks / (float) totalTicks) * 1.5f + 0.5f;
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
        if (!save.debug && alive.size() == 1) {
            save.state = State.END;
            Player winner = alive.get(0);
            save.winner = winner.getUniqueId();
            if (save.event) {
                save.addScore(winner.getUniqueId(), 1);
                computeHighscores();
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(empty());
                online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, space(), winner.displayName(), text(" wins the game!", GREEN)));
                online.sendMessage(empty());
                online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
            }
        } else if (!save.debug && alive.isEmpty()) {
            save.state = State.END;
            save.winner = null;
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(empty());
                online.sendMessage(textOfChildren(Mytems.CAPTAINS_CUTLASS, text(" The game ends in a draw!", DARK_RED)));
                online.sendMessage(empty());
                online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 0.5f, 1.0f);
            }
        }
        if (save.dropCooldown <= 0) {
            drop();
            save.dropCooldown = 100;
        } else {
            save.dropCooldown -= 1;
        }
        if (save.gameTicks % 20 == 0) {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, save.tickSpeed);
            save.tickSpeed += 1;
        }
        int seconds = save.gameTicks / 20;
        int minutes = seconds / 60;
        timeString = minutes + "m " + (seconds % 60) + "s";
        save.gameTicks += 1;
    }

    private static final List<ItemStack> DROP_ITEMS = List.of(new ItemStack(Material.FLINT_AND_STEEL),
                                                              new ItemStack(Material.TNT),
                                                              new ItemStack(Material.IRON_AXE),
                                                              new ItemStack(Material.APPLE, 4),
                                                              new ItemStack(Material.APPLE, 8),
                                                              new ItemStack(Material.APPLE, 16),
                                                              new ItemStack(Material.BREAD, 4),
                                                              new ItemStack(Material.GOLDEN_APPLE),
                                                              new ItemStack(Material.ELYTRA),
                                                              new ItemStack(Material.LAVA_BUCKET));

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
        if (random.nextInt(20) == 0) {
            if (random.nextBoolean()) {
                getLogger().info("Dropping TNT Minecart at " + vec);
                world.spawnEntity(location, EntityType.MINECART_TNT);
            } else {
                getLogger().info("Dropping Falling Lava at " + vec);
                world.spawnFallingBlock(location, Material.LAVA.createBlockData());
            }
        } else {
            ItemStack item = DROP_ITEMS.get(random.nextInt(DROP_ITEMS.size()));
            getLogger().info("Dropping " + item.getType() + " at " + vec);
            world.dropItem(location, item.clone());
        }
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
                if (pirate.respawnCooldown > 0 && (pirate.respawnCooldown % 20) == 0) {
                    int seconds = pirate.respawnCooldown / 20;
                    player.sendActionBar(text("Respawn in " + seconds, GREEN));
                }
            }
            return;
        }
        // Death Checks
        if (player.getLocation().getBlock().isLiquid()) {
            player.getWorld().spawnParticle(Particle.WATER_WAKE, player.getLocation(), 32, 0.1, 0.1, 0.1, 0.2);
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
        player.setFoodLevel(0);
        player.setGameMode(GameMode.SPECTATOR);
        Pirate pirate = save.pirates.get(player.getUniqueId());
        if (pirate == null) return;
        pirate.respawnCooldown = 200;
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

    protected void computeHighscores() {
        highscores = Highscore.of(save.scores);
        highscoreLines = Highscore.sidebar(highscores, TrophyCategory.SWORD);
    }
}

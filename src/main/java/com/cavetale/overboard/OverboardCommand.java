package com.cavetale.overboard;

import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class OverboardCommand implements TabExecutor {
    private final OverboardPlugin plugin;

    public void enable() {
        plugin.getCommand("overboard").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "start":
            if (args.length != 2) return false;
            final String path = args[1];
            final BuildWorld buildWorld = BuildWorld.findWithPath(path);
            if (buildWorld == null) {
                sender.sendMessage(text("World not found: " + path, RED));
                return true;
            }
            if (buildWorld.getRow().parseMinigame() != MinigameMatchType.OVERBOARD) {
                sender.sendMessage(text("Not an Overboard world: " + buildWorld.getRow().getName(), RED));
                return true;
            }
            plugin.startGame(buildWorld);
            sender.sendMessage(text("Starting game in " + buildWorld.getRow().getName(), YELLOW));
            return true;
        case "stop":
            plugin.stopGame();
            sender.sendMessage(text("Stopping game", YELLOW));
            return true;
        case "debug":
            if (args.length == 2) {
                plugin.save.debug = args[1].equals("true") || args[1].startsWith("enable");
                plugin.save();
            }
            sender.sendMessage(text("Debug: " + plugin.save.debug, YELLOW));
            return true;
        case "event":
            if (args.length == 2) {
                plugin.save.event = args[1].equals("true") || args[1].startsWith("enable");
                plugin.save();
            }
            sender.sendMessage(text("Event: " + plugin.save.event, YELLOW));
            return true;
        case "save":
            plugin.save();
            sender.sendMessage(text("Saved!", YELLOW));
            return true;
        case "resetscore":
            plugin.save.scores.clear();
            plugin.computeHighscores();
            plugin.save();
            sender.sendMessage(text("Scores reset!", YELLOW));
            return true;
        case "rewardscores": {
            int count = Highscore.reward(plugin.save.scores,
                                         "overboard",
                                         TrophyCategory.SWORD,
                                         plugin.TITLE,
                                         hi -> "You scored " + hi.score + " point" + (hi.score == 1 ? "" : "s"));
            sender.sendMessage(text("Rewarded " + count + " players!", YELLOW));
            return true;
        }
        case "skip":
            switch (plugin.save.state) {
            case WARMUP:
                plugin.save.warmupTicks = plugin.WARMUP_TICKS;
                sender.sendMessage(text("Skipping warmup", YELLOW));
                break;
            case IDLE: default:
                sender.sendMessage(text("Game cannot skip!", RED));
            }
            return true;
        default: return false;
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        final List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String argl = args[0].toLowerCase();
            for (String arg : List.of("start", "stop", "debug", "event", "event", "save", "resetscore", "rewardscores", "skip")) {
                if (arg.contains(argl)) result.add(arg);
            }
            return result;
        }
        if (args.length == 2 && args[0].equals("start")) {
            final String lower = args[1].toLowerCase();
            for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(MinigameMatchType.OVERBOARD, false)) {
                final String path = buildWorld.getPath();
                if (path.toLowerCase().contains(lower)) {
                    result.add(path);
                }
            }
        }
        return result;
    }
}

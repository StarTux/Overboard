package com.cavetale.overboard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

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
            plugin.startGame();
            sender.sendMessage("Starting game");
            return true;
        case "stop":
            plugin.stopGame();
            sender.sendMessage("Stopping game");
            return true;
        default: return false;
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return null;
    }
}

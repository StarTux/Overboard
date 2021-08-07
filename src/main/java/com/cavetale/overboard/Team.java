package com.cavetale.overboard;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

public enum Team {
    RED(0xFF0000),
    BLUE(0x0000FF);

    public final String displayName;
    public final TextColor color;
    public final Color bukkitColor;

    Team(final int rgb) {
        this.displayName = name().substring(0, 1) + name().substring(1).toLowerCase();
        this.color = TextColor.color(rgb);
        this.bukkitColor = Color.fromRGB(rgb);
    }
}

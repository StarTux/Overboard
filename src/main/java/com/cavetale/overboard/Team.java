package com.cavetale.overboard;

import net.kyori.adventure.text.format.TextColor;

public enum Team {
    RED(TextColor.color(0xFF0000)),
    BLUE(TextColor.color(0x0000FF));

    public final String displayName;
    public final TextColor color;

    Team(final TextColor color) {
        this.displayName = name().substring(0, 1) + name().substring(1).toLowerCase();
        this.color = color;
    }
}

package com.cavetale.overboard;

import com.cavetale.core.event.player.PlayerTeamQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum PirateTeam {
    RED("Red", NamedTextColor.RED),
    BLUE("Blue", NamedTextColor.BLUE);

    public final String key;
    public final String displayName;
    public final NamedTextColor color;
    public final Component component;
    public final PlayerTeamQuery.Team queryTeam;

    PirateTeam(final String displayName, final NamedTextColor color) {
        this.key = name().toLowerCase();
        this.displayName = displayName;
        this.color = color;
        this.component = Component.text(displayName, color);
        this.queryTeam = new PlayerTeamQuery.Team("overboard:" + key,
                                                  Component.text(displayName, color),
                                                  color);
    }
}

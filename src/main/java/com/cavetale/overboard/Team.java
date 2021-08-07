package com.cavetale.overboard;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

public enum Team {
    RED(0xFF0000, Material.RED_BED),
    BLUE(0x0000FF, Material.BLUE_BED);

    public final String displayName;
    public final TextColor color;
    public final Color bukkitColor;
    public final Material bedMaterial;

    Team(final int rgb, final Material bedMaterial) {
        this.displayName = name().substring(0, 1) + name().substring(1).toLowerCase();
        this.color = TextColor.color(rgb);
        this.bukkitColor = Color.fromRGB(rgb);
        this.bedMaterial = bedMaterial;
    }
}

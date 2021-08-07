package com.cavetale.overboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public final class Items {
    private Items() { }

    public static ItemStack dye(Material mat, Team team) {
        ItemStack item = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(team.bukkitColor);
        meta.displayName(Component.text(team.displayName, team.color));
        item.setItemMeta(meta);
        return item;
    }
}

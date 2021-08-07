package com.cavetale.overboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;
import org.bukkit.entity.Player;

// JSON
@Data
public final class Save {
    protected State state = State.IDLE;
    protected int ticks = 0;
    protected Map<UUID, Pirate> players = new HashMap<>();
    protected List<Cannon> cannons = new ArrayList<>();

    public Pirate getPirate(Player player) {
        return players.get(player.getUniqueId());
    }

    public Cannon cannonAt(Vec3i vector) {
        for (Cannon cannon : cannons) {
            if (Objects.equals(vector, cannon.vector)) return cannon;
        }
        return null;
    }
}

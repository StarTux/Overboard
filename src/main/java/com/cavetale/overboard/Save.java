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
    protected Map<Team, TeamSave> teams = new HashMap<>();
    protected int explosiveCooldown = 0;
    protected int dropCooldown = 0;
    protected int highestScore = 0;
    protected UUID highestScorePlayer = null;
    protected Team highestScoreTeam = null;
    protected boolean debug;

    public Pirate getPirate(Player player) {
        return players.get(player.getUniqueId());
    }

    public Cannon cannonAt(Vec3i vector) {
        for (Cannon cannon : cannons) {
            if (Objects.equals(vector, cannon.vector)) return cannon;
        }
        return null;
    }

    void newGame() {
        state = State.WARMUP;
        ticks = 0;
        players.clear();
        cannons.clear();
        explosiveCooldown = 0;
        dropCooldown = 0;
        highestScore = 0;
        highestScorePlayer = null;
    }
}

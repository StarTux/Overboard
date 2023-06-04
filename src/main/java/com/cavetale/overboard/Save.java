package com.cavetale.overboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public final class Save {
    // Debug
    protected State state = State.IDLE;
    protected UUID winner;
    protected boolean debug;
    protected boolean event;
    protected String worldName;
    protected Map<UUID, Pirate> pirates = new HashMap<>();
    protected Map<UUID, Integer> scores = new HashMap<>();
    protected int ticks = 0;
    protected int warmupTicks;
    protected int gameTicks;
    protected int endTicks;
    protected int dropCooldown;
    protected int tickSpeed;

    public void addScore(UUID uuid, int value) {
        int score = scores.getOrDefault(uuid, 0) + value;
        scores.put(uuid, Math.max(0, score));
    }
}

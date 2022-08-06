package com.cavetale.overboard;

import com.cavetale.core.struct.Vec3i;

/**
 * Player data stored in Save.
 */
public final class Pirate {
    protected Team team;
    protected Vec3i bed1;
    protected Vec3i bed2;
    protected int respawnCooldown;
    protected int score;

    public Pirate() { }

    public Pirate(final Team team) {
        this.team = team;
    }
}
